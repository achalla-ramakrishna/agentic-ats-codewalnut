package com.codewalnut.ats.service;

import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.client.ProfileRanker;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationSource;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.dto.NaukriDtos.AddedRow;
import com.codewalnut.ats.dto.NaukriDtos.ImportItem;
import com.codewalnut.ats.dto.NaukriDtos.ImportRequest;
import com.codewalnut.ats.dto.NaukriDtos.ImportResult;
import com.codewalnut.ats.dto.NaukriDtos.RankResult;
import com.codewalnut.ats.dto.NaukriDtos.RankedRow;
import com.codewalnut.ats.dto.NaukriDtos.RowStatus;
import com.codewalnut.ats.naukri.NaukriProfile;
import com.codewalnut.ats.naukri.NaukriSheet;
import com.codewalnut.ats.naukri.RuleRanker;
import com.codewalnut.ats.naukri.SpreadsheetReader;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Naukri applicant exports (ADR-0026): rank every row against the opening, then add only the rows
 * a recruiter picks as candidates. The file itself is never stored; rows not picked leave no trace.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaukriImportService {

    static final int BATCH = 20;
    static final int MAX_AI_ROWS = 400;

    private final AccessPolicy accessPolicy;
    private final TrackerService trackerService;
    private final ApplicationRepository applicationRepository;
    private final CandidateRepository candidateRepository;
    private final AuditService auditService;
    private final ProfileRanker ranker;

    @Transactional(readOnly = true)
    public RankResult rank(AppUser actor, UUID jobId, MultipartFile file) throws IOException {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = trackerService.openJob(jobId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose the Excel file exported from Naukri.");
        }
        NaukriSheet.Parsed parsed = NaukriSheet.parse(SpreadsheetReader.read(file.getOriginalFilename(), file.getBytes()));
        List<NaukriProfile> profiles = parsed.profiles();
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("The sheet has a heading row but no applicants under it.");
        }

        // Rules first: a score for everyone, and the order for choosing whom the AI reads when there are many.
        Map<Integer, RuleRanker.Score> rules = new HashMap<>();
        for (int i = 0; i < profiles.size(); i++) {
            rules.put(i, RuleRanker.score(job.getTitle(), job.getDescription(), job.getLocation(), profiles.get(i)));
        }
        Map<Integer, ProfileRanker.Score> ai = new HashMap<>();
        String rankedBy = "RULES";
        String note = null;
        if (ranker.available()) {
            List<Integer> order = new ArrayList<>(rules.keySet());
            order.sort(Comparator.comparingInt((Integer i) -> rules.get(i).percent()).reversed());
            List<Integer> forAi = order.subList(0, Math.min(order.size(), MAX_AI_ROWS));
            int failed = scoreWithAi(job, profiles, forAi, ai);
            if (ai.isEmpty()) {
                note = "The AI couldn't be reached, so this ranking uses skills, experience, title, location and notice period from the sheet.";
            } else {
                rankedBy = "AI";
                int byRules = profiles.size() - ai.size();
                if (byRules > 0) {
                    note = byRules + " row" + (byRules == 1 ? " was" : "s were") + " ranked by rules"
                            + (failed > 0 ? " because the AI was busy" : " (the AI reads the top " + MAX_AI_ROWS + ")") + "; their scores are less precise.";
                }
            }
        } else {
            note = "Ranked by rules (skills, experience, title, location and notice period). Set up the AI for a sharper ranking.";
        }

        Set<String> seen = new HashSet<>();
        List<RankedRow> rows = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            NaukriProfile p = profiles.get(i);
            ProfileRanker.Score s = ai.get(i);
            RuleRanker.Score r = rules.get(i);
            int percent = s != null ? clamp(s.matchPercent()) : r.percent();
            String summary = s != null ? s.summary() : r.strengths().isEmpty() ? "Few of the opening's skills in the profile" : r.strengths().get(0);
            // By rules the summary is the first strength, so it isn't repeated in the list.
            List<String> strengths = s != null ? limit(s.strengths())
                    : limit(r.strengths().isEmpty() ? List.of() : r.strengths().subList(1, r.strengths().size()));
            List<String> gaps = s != null ? limit(s.gaps()) : limit(r.gaps());
            rows.add(new RankedRow(p, percent, summary, strengths, gaps, status(jobId, p, seen)));
        }
        rows.sort(Comparator.comparingInt(RankedRow::matchPercent).reversed()
                .thenComparing(r -> r.profile().experienceYears() == null ? 0 : -r.profile().experienceYears()));
        return new RankResult(file.getOriginalFilename(), rankedBy, note, StringUtils.hasText(job.getDescription()),
                profiles.size(), parsed.missing(), rows);
    }

    /** Scores in batches, a few at a time. Returns how many batches failed. */
    private int scoreWithAi(JobOpening job, List<NaukriProfile> profiles, List<Integer> indexes, Map<Integer, ProfileRanker.Score> out) {
        ProfileRanker.Job j = new ProfileRanker.Job(job.getTitle(), job.getDescription(), job.getLocation());
        List<List<Integer>> batches = new ArrayList<>();
        for (int i = 0; i < indexes.size(); i += BATCH) {
            batches.add(indexes.subList(i, Math.min(indexes.size(), i + BATCH)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, Math.max(1, batches.size())));
        int failed = 0;
        try {
            List<Future<List<ProfileRanker.Score>>> futures = new ArrayList<>();
            for (List<Integer> batch : batches) {
                List<ProfileRanker.Profile> in = batch.stream().map(i -> new ProfileRanker.Profile(i, profiles.get(i).text())).toList();
                futures.add(pool.submit(() -> ranker.rank(j, in)));
            }
            for (int b = 0; b < futures.size(); b++) {
                try {
                    Set<Integer> wanted = new HashSet<>(batches.get(b));
                    for (ProfileRanker.Score s : futures.get(b).get(5, TimeUnit.MINUTES)) {
                        if (wanted.contains(s.id())) {
                            out.put(s.id(), s);
                        }
                    }
                } catch (Exception e) {
                    failed++;
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.warn("Naukri ranking batch failed: {}", cause instanceof CalendarException ? cause.getMessage() : cause.toString());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return failed;
    }

    private RowStatus status(UUID jobId, NaukriProfile p, Set<String> seen) {
        if (p.name() == null || (p.email() == null && p.phone() == null)) {
            return RowStatus.NO_CONTACT;
        }
        String key = p.email() != null ? p.email() : "tel:" + p.phone();
        if (!seen.add(key)) {
            return RowStatus.DUPLICATE_IN_FILE;
        }
        Optional<Candidate> existing = trackerService.findExisting(p.email(), p.phone());
        if (existing.isEmpty()) {
            return RowStatus.NEW;
        }
        return applicationRepository.existsByJobIdAndCandidateId(jobId, existing.get().getId())
                ? RowStatus.ALREADY_IN_OPENING : RowStatus.EXISTING_CANDIDATE;
    }

    /** Adds the chosen rows as candidates in the opening, with the Naukri details as the first note. */
    @Transactional
    public ImportResult importRows(AppUser actor, UUID jobId, ImportRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = trackerService.openJob(jobId);
        List<AddedRow> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ImportItem item : request.rows()) {
            NaukriProfile p = item.profile();
            String name = p.name() == null ? null : p.name().strip().replaceAll("\\s+", " ");
            String email = NaukriSheet.email(p.email());
            String phone = NaukriSheet.phone(p.phone());
            if (!StringUtils.hasText(name) || (email == null && phone == null)) {
                skipped.add((name == null ? "Row " + p.line() : name) + ": no valid email or phone");
                continue;
            }
            if (!seen.add(email != null ? email : "tel:" + phone)) {
                skipped.add(name + ": listed twice");
                continue;
            }
            Optional<Candidate> existing = trackerService.findExisting(email, phone);
            if (existing.isPresent() && applicationRepository.existsByJobIdAndCandidateId(jobId, existing.get().getId())) {
                skipped.add(name + ": already in this opening");
                continue;
            }
            Candidate candidate = existing.orElseGet(() -> candidateRepository.save(Candidate.builder()
                    .name(name).email(email).phone(phone)
                    .degree(trim(p.ugDegree(), 200)).college(trim(p.ugCollege(), 200)).build()));
            fillMissingContact(candidate, email, phone);
            Application application = trackerService.createApplication(actor.getEmail(), job, candidate, request.stage(),
                    note(p, item), ApplicationSource.NAUKRI, null);
            added.add(new AddedRow(application.getId(), candidate.getName(), candidate.getEmail(), candidate.getEmail() != null));
        }
        if (!added.isEmpty()) {
            auditService.record(actor, AuditAction.CANDIDATES_IMPORTED, "JobOpening", jobId,
                    Map.of("added", added.size(), "stage", request.stage(), "source", "naukri"));
        }
        return new ImportResult(added.size(), added, skipped);
    }

    /** A known person with no email or phone on file gets the one from the sheet (if nobody else has it). */
    private void fillMissingContact(Candidate candidate, String email, String phone) {
        if (candidate.getEmail() == null && email != null && candidateRepository.findByEmail(email).isEmpty()) {
            candidate.setEmail(email);
        }
        if (candidate.getPhone() == null && phone != null) {
            candidate.setPhone(phone);
        }
    }

    /** The first note on the candidate: where they came from, the match and the job-related details. */
    static String note(NaukriProfile p, ImportItem item) {
        List<String> lines = new ArrayList<>();
        lines.add("From Naukri" + (p.appliedOn() == null ? "" : " (applied " + p.appliedOn() + ")")
                + (item.matchPercent() == null ? "" : " · Match " + item.matchPercent() + "%")
                + (StringUtils.hasText(item.summary()) ? " — " + item.summary().strip() : ""));
        List<String> facts = new ArrayList<>();
        if (p.experience() != null) {
            facts.add("Experience: " + p.experience());
        }
        if (p.designation() != null || p.company() != null) {
            facts.add("Now: " + (p.designation() == null ? "" : p.designation()) + (p.company() == null ? "" : " at " + p.company()));
        }
        if (p.noticePeriod() != null) {
            facts.add("Notice: " + p.noticePeriod());
        }
        if (p.salary() != null) {
            facts.add("Salary: " + p.salary());
        }
        if (p.currentLocation() != null) {
            facts.add("Location: " + p.currentLocation() + (p.preferredLocations() == null ? "" : " (prefers " + p.preferredLocations() + ")"));
        }
        if (!facts.isEmpty()) {
            lines.add(String.join(" · ", facts));
        }
        if (p.headline() != null) {
            lines.add("Headline: " + p.headline());
        }
        if (p.keySkills() != null) {
            lines.add("Key skills: " + p.keySkills());
        }
        String note = String.join("\n", lines);
        return note.length() > 4900 ? note.substring(0, 4900) : note;
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static List<String> limit(List<String> items) {
        return items == null ? List.of() : items.stream().filter(StringUtils::hasText).limit(3).toList();
    }

    private static String trim(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
