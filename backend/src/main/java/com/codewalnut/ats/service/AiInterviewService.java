package com.codewalnut.ats.service;

import com.codewalnut.ats.client.BuiltInInterviewAgent;
import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.client.InterviewAgent;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.Assessment;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.AssessmentQuestion;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.BankQuestion;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.domain.InterviewPhase;
import com.codewalnut.ats.domain.InterviewVideo;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.dto.AiInterviewDtos.CreateInterviewRequest;
import com.codewalnut.ats.dto.AiInterviewDtos.CreateInterviewResult;
import com.codewalnut.ats.dto.AiInterviewDtos.JobInterviews;
import com.codewalnut.ats.dto.AiInterviewDtos.RankedCandidate;
import com.codewalnut.ats.dto.AiInterviewDtos.VideoSaved;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.AssessmentInviteRepository;
import com.codewalnut.ats.repository.AssessmentQuestionRepository;
import com.codewalnut.ats.repository.AssessmentRepository;
import com.codewalnut.ats.repository.BankQuestionRepository;
import com.codewalnut.ats.repository.InterviewVideoRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.repository.MessageRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI interviews (ADR-0030): a test of kind INTERVIEW whose questions run in phases and may be
 * answered on video. This builds one for an opening, stores the videos, prepares and records the
 * agent's review, and ranks an opening's candidates. Everything here is advisory: it ranks and
 * explains; people shortlist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInterviewService {

    static final long MAX_VIDEO_BYTES = 60L * 1024 * 1024;
    static final int MAX_REVIEW_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TECH_MCQS = 6;
    private static final int DESIGN_MCQS = 3;

    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AssessmentInviteRepository inviteRepository;
    private final InterviewVideoRepository videoRepository;
    private final ApplicationRepository applicationRepository;
    private final JobOpeningRepository jobRepository;
    private final BankQuestionRepository bankRepository;
    private final MessageRepository messageRepository;
    private final AssessmentService assessmentService;
    private final AssessmentInviteService inviteService;
    private final InterviewReviews reviews;
    private final VideoStorage storage;
    private final InterviewAgent agent;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    // ---- building an interview for an opening ----

    /** Drafts a full interview for the opening as a draft test, for a person to check, edit and mark ready. */
    @Transactional
    public CreateInterviewResult create(AppUser actor, CreateInterviewRequest request) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        JobOpening job = jobRepository.findById(request.jobId()).orElseThrow(() -> new NotFoundException("Opening not found"));
        InterviewAgent.Job forAgent = new InterviewAgent.Job(job.getTitle(), job.getDescription());
        List<String> notes = new ArrayList<>();
        String text = (job.getTitle() + " " + job.getDescription()).toLowerCase(Locale.ROOT);
        Assessment.Category area = stack(text);
        BankQuestion.Section band = text.matches("(?s).*\\b(senior|lead|architect|principal|staff)\\b.*")
                ? BankQuestion.Section.ADVANCED : BankQuestion.Section.PRACTICAL;
        // A hand-written set (marked answers checked by hand) beats a drafted one where it fits.
        boolean handWritten = area == Assessment.Category.JAVA && band == BankQuestion.Section.ADVANCED;
        InterviewAgent.Draft draft;
        boolean drafted = !handWritten && agent.usesAi();
        if (handWritten) {
            draft = new InterviewAgent.Draft(com.codewalnut.ats.bank.InterviewSets.seniorJavaLead());
            notes.add("Used the hand-written senior Java / lead question set: production scenarios, code reading and "
                    + "architecture, with what a strong answer covers for the reviewers. Edit anything you like.");
        } else {
            try {
                draft = agent.draft(forAgent);
            } catch (CalendarException e) {
                notes.add(e.getMessage() + " The standard question set was used instead — edit it to fit the opening.");
                draft = new BuiltInInterviewAgent().draft(forAgent);
                drafted = false;
            }
        }
        String title = "AI interview — " + job.getTitle();
        Assessment a = assessmentRepository.save(Assessment.builder()
                .title(title.length() > 200 ? title.substring(0, 200) : title)
                .category(Assessment.Category.INTERVIEW)
                .description("A recorded interview for the " + job.getTitle() + " role, in phases: introduction, work experience, "
                        + "skills, projects, architecture, a technical round, soft skills and readiness to join. Most questions are "
                        + "answered on camera (use Chrome or Edge, with a working camera and microphone, in a quiet place); a few are "
                        + "multiple choice or fill in the blank. You can record an answer again before moving on.")
                .durationMinutes(request.durationMinutes() == null ? 75 : request.durationMinutes())
                .passPercent(0)
                .status(Assessment.Status.DRAFT)
                .createdBy(actor.getEmail())
                .build());
        Map<InterviewPhase, List<AssessmentQuestion>> byPhase = new LinkedHashMap<>();
        for (InterviewPhase p : InterviewPhase.values()) {
            byPhase.put(p, new ArrayList<>());
        }
        int dropped = 0;
        for (InterviewAgent.DraftQuestion d : draft.questions() == null ? List.<InterviewAgent.DraftQuestion>of() : draft.questions()) {
            AssessmentQuestion q = fromDraft(a.getId(), d, drafted, handWritten);
            if (q == null) {
                dropped++;
                continue;
            }
            byPhase.get(InterviewPhase.of(q.getSection())).add(q);
        }
        if (dropped > 0) {
            notes.add(dropped + " drafted question(s) were incomplete and left out.");
        }
        if (handWritten) {
            // its own multiple choice
        } else if (area != null) {
            byPhase.get(InterviewPhase.TECHNICAL).addAll(0, fromBank(a.getId(), area, band, TECH_MCQS, InterviewPhase.TECHNICAL));
        } else {
            notes.add("No question bank matches this opening's stack, so the technical round has no multiple-choice questions. Add some if you like.");
        }
        if (!handWritten) {
            byPhase.get(InterviewPhase.ARCHITECTURE).addAll(fromBank(a.getId(), Assessment.Category.SYSTEM_DESIGN, band, DESIGN_MCQS,
                    InterviewPhase.ARCHITECTURE));
        }
        int position = 0;
        for (List<AssessmentQuestion> list : byPhase.values()) {
            for (AssessmentQuestion q : list) {
                q.setPosition(++position);
                questionRepository.save(q);
            }
        }
        auditService.record(actor, AuditAction.ASSESSMENT_CREATED, "Assessment", a.getId(),
                Map.of("interview", true, "jobId", job.getId(), "questions", position, "ai", drafted));
        return new CreateInterviewResult(assessmentService.detail(a), notes);
    }

    /** trusted: a hand-written set, whose multiple choice may be used as is (drafted multiple choice never is). */
    private AssessmentQuestion fromDraft(UUID assessmentId, InterviewAgent.DraftQuestion d, boolean aiDrafted, boolean trusted) {
        InterviewPhase phase = d == null ? null : InterviewPhase.of(d.phase() == null ? null : d.phase().strip().toUpperCase(Locale.ROOT));
        String prompt = d == null || d.prompt() == null ? "" : d.prompt().strip();
        if (phase == null || prompt.isEmpty() || prompt.length() > 2000) {
            return null;
        }
        String code = d.code() == null || d.code().isBlank() || d.code().length() > 5000 ? null : d.code().strip();
        String good = d.goodAnswer() == null || d.goodAnswer().isBlank() ? null : d.goodAnswer().strip();
        AssessmentQuestion.AssessmentQuestionBuilder q = AssessmentQuestion.builder().assessmentId(assessmentId).prompt(prompt)
                .code(code).explanation(good == null || good.length() > 2000 ? null : good)
                .section(phase.name()).aiDrafted(aiDrafted);
        String kind = d.kind() == null ? "" : d.kind().strip().toUpperCase(Locale.ROOT);
        switch (kind) {
            case "VIDEO" -> q.kind(AssessmentQuestion.Kind.VIDEO).points(0).answerJson("[]")
                    .maxSeconds(Math.max(60, Math.min(180, d.maxSeconds() <= 0 ? 120 : d.maxSeconds())));
            case "WRITTEN" -> q.kind(AssessmentQuestion.Kind.WRITTEN).points(0).answerJson("[]");
            case "FILL_BLANK" -> {
                List<String> accepted = d.acceptedAnswers() == null ? List.of() : d.acceptedAnswers().stream()
                        .filter(s -> s != null && !s.isBlank() && s.length() <= 100).map(String::strip).distinct().limit(3).toList();
                if (accepted.isEmpty() || !prompt.contains("___")) {
                    return null;
                }
                q.kind(AssessmentQuestion.Kind.SHORT_ANSWER).points(1).answerJson(assessmentService.write(accepted));
            }
            case "MULTIPLE_CHOICE" -> {
                List<String> options = d.options() == null ? List.of() : d.options().stream().filter(o -> o != null && !o.isBlank()).toList();
                if (!trusted || options.size() < 2 || options.size() > 8 || d.correctOption() < 0 || d.correctOption() >= options.size()) {
                    return null;
                }
                q.kind(AssessmentQuestion.Kind.SINGLE_CHOICE).points(2).optionsJson(assessmentService.write(options))
                        .answerJson(assessmentService.write(List.of(d.correctOption())));
            }
            default -> {
                return null;
            }
        }
        return q.build();
    }

    /** Hand-written multiple-choice questions from the bank (answers checked), least used first. */
    private List<AssessmentQuestion> fromBank(UUID assessmentId, Assessment.Category area, BankQuestion.Section band, int n, InterviewPhase phase) {
        List<BankQuestion> pool = new ArrayList<>();
        for (BankQuestion.Difficulty d : List.of(BankQuestion.Difficulty.MEDIUM, BankQuestion.Difficulty.HARD)) {
            pool.addAll(bankRepository.findByAreaAndSectionAndDifficultyAndStatus(area, band, d, BankQuestion.Status.ACTIVE));
        }
        pool.removeIf(b -> b.getKind() != AssessmentQuestion.Kind.SINGLE_CHOICE || b.getOptionFiguresJson() != null);
        Collections.shuffle(pool, RANDOM);
        pool.sort(Comparator.comparingInt(BankQuestion::getTimesUsed));
        List<AssessmentQuestion> out = new ArrayList<>();
        for (BankQuestion b : pool.subList(0, Math.min(n, pool.size()))) {
            b.setTimesUsed(b.getTimesUsed() + 1);
            out.add(AssessmentQuestion.builder().assessmentId(assessmentId).kind(b.getKind()).prompt(b.getPrompt()).code(b.getCode())
                    .optionsJson(b.getOptionsJson()).answerJson(b.getAnswerJson()).points(b.getPoints()).explanation(b.getExplanation())
                    .figure(b.getFigure()).section(phase.name()).topic(b.getTopic()).difficulty(b.getDifficulty().name())
                    .bankQuestionId(b.getId()).aiDrafted(false).build());
        }
        return out;
    }

    /** The question bank that fits the opening, or null. */
    static Assessment.Category stack(String text) {
        if (text.matches("(?s).*\\bjava\\b.*") || text.contains("spring boot")) {
            return Assessment.Category.JAVA;
        }
        if (text.contains("python") || text.contains("django")) {
            return Assessment.Category.PYTHON;
        }
        if (text.contains("node")) {
            return Assessment.Category.NODEJS;
        }
        if (text.contains("react")) {
            return Assessment.Category.REACT;
        }
        if (text.contains("angular")) {
            return Assessment.Category.ANGULAR;
        }
        if (text.contains("javascript") || text.contains("typescript")) {
            return Assessment.Category.JAVASCRIPT;
        }
        return null;
    }

    // ---- video answers ----

    /** The candidate's recording for one question; recording again replaces it. */
    @Transactional
    public VideoSaved upload(CandidateAccount account, UUID inviteId, UUID questionId, MultipartFile file, String transcript, int seconds)
            throws IOException {
        AssessmentInvite invite = inviteService.ownInvite(account, inviteId);
        if (inviteService.finalise(invite) != AssessmentInvite.Status.STARTED) {
            throw new IllegalArgumentException("This interview isn't open any more.");
        }
        AssessmentQuestion q = questionRepository.findById(questionId)
                .filter(x -> x.getAssessmentId().equals(invite.getAssessment().getId()) && x.getKind() == AssessmentQuestion.Kind.VIDEO)
                .orElseThrow(() -> new NotFoundException("Question not found"));
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file: the recording is empty — record again");
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new IllegalArgumentException("file: the recording is too large — keep the answer shorter");
        }
        int limit = q.getMaxSeconds() == null ? 180 : q.getMaxSeconds();
        int length = Math.max(1, Math.min(seconds, limit));
        String extension;
        String contentType;
        String key;
        long size;
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            in.mark(16);
            byte[] head = in.readNBytes(12);
            in.reset();
            extension = videoType(head);
            if (extension == null) {
                throw new IllegalArgumentException("file: only WebM or MP4 recordings are accepted");
            }
            contentType = "webm".equals(extension) ? "video/webm" : "video/mp4";
            key = "interviews/" + inviteId + "/" + questionId + "-" + Long.toHexString(RANDOM.nextLong()) + "." + extension;
            size = storage.save(key, in);
        }
        InterviewVideo video = videoRepository.findByInviteIdAndQuestionId(inviteId, questionId).orElse(null);
        String old = video == null ? null : video.getStorageKey();
        if (video == null) {
            video = InterviewVideo.builder().inviteId(inviteId).questionId(questionId).build();
        }
        video.setStorageKey(key);
        video.setContentType(contentType);
        video.setSizeBytes(size);
        video.setDurationSeconds(length);
        video = videoRepository.save(video);
        if (old != null) {
            storage.delete(old);
        }
        Map<UUID, List<String>> answers = new LinkedHashMap<>(inviteService.answers(invite));
        String text = transcript == null ? "" : transcript.strip();
        answers.put(questionId, List.of(video.getId().toString(),
                text.length() > AssessmentInviteService.MAX_OPEN_ANSWER ? text.substring(0, AssessmentInviteService.MAX_OPEN_ANSWER) : text));
        invite.setAnswersJson(write(answers));
        auditService.recordAnonymous(account.getEmail(), AuditAction.INTERVIEW_VIDEO_UPLOADED,
                Map.of("inviteId", inviteId, "questionId", questionId, "seconds", length));
        return new VideoSaved(video.getId(), length, size);
    }

    /** "webm" / "mp4" from the file's first bytes, or null. */
    static String videoType(byte[] head) {
        if (head.length >= 4 && (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45 && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3) {
            return "webm";
        }
        if (head.length >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            return "mp4";
        }
        return null;
    }

    public record VideoFile(Resource resource, String contentType) {}

    /** Staff: a recorded answer, for the player. */
    @Transactional(readOnly = true)
    public VideoFile video(AppUser actor, UUID inviteId, UUID videoId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        InterviewVideo v = videoRepository.findById(videoId).filter(x -> x.getInviteId().equals(inviteId))
                .orElseThrow(() -> new NotFoundException("Video not found"));
        Resource r = storage.load(v.getStorageKey());
        if (r == null) {
            throw new NotFoundException("The video file is missing (was ATS_MEDIA_DIR on a volume?)");
        }
        return new VideoFile(r, v.getContentType());
    }

    // ---- the review (runs after submit, AiInterviewReviewer) ----

    public record ReviewWork(InterviewAgent.Job job, List<InterviewAgent.Answer> answers) {}

    /** Everything the agent needs, or null when the interview isn't waiting for a review. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ReviewWork reviewWork(UUID inviteId) {
        AssessmentInvite invite = inviteRepository.findById(inviteId).orElse(null);
        if (invite == null || invite.getEvaluation() != AssessmentInvite.Evaluation.PENDING) {
            return null;
        }
        Map<UUID, List<String>> given = inviteService.answers(invite);
        Map<UUID, Integer> seconds = videoRepository.findByInviteId(inviteId).stream()
                .collect(Collectors.toMap(InterviewVideo::getQuestionId, InterviewVideo::getDurationSeconds, (x, y) -> x));
        List<InterviewAgent.Answer> answers = new ArrayList<>();
        for (AssessmentQuestion q : questionRepository.findByAssessmentIdOrderByPositionAsc(invite.getAssessment().getId())) {
            List<String> g = given.getOrDefault(q.getId(), List.of());
            String phase = q.getSection() == null ? "OTHER" : q.getSection();
            String asked = q.getCode() == null ? q.getPrompt() : q.getPrompt() + "\nCode shown:\n" + q.getCode();
            switch (q.getKind()) {
                case VIDEO -> {
                    boolean recorded = !g.isEmpty() && !g.get(0).isBlank();
                    String transcript = g.size() > 1 ? g.get(1) : "";
                    answers.add(new InterviewAgent.Answer(phase, "VIDEO", asked,
                            !recorded ? "" : transcript.isBlank() ? "(answered on video; no transcript available)" : transcript,
                            seconds.get(q.getId()), null, q.getExplanation()));
                }
                case WRITTEN -> answers.add(new InterviewAgent.Answer(phase, "WRITTEN", asked, g.isEmpty() ? "" : g.get(0), null, null,
                        q.getExplanation()));
                case SHORT_ANSWER -> answers.add(new InterviewAgent.Answer(phase, "FILL_BLANK", q.getPrompt(), g.isEmpty() ? "" : g.get(0), null,
                        assessmentService.earned(q, g) > 0));
                case SINGLE_CHOICE, MULTI_CHOICE -> {
                    List<String> options = assessmentService.options(q);
                    String picked = g.stream().map(s -> {
                        try {
                            int i = Integer.parseInt(s.strip());
                            return i >= 0 && i < options.size() ? options.get(i) : "";
                        } catch (NumberFormatException e) {
                            return "";
                        }
                    }).filter(s -> !s.isEmpty()).collect(Collectors.joining("; "));
                    answers.add(new InterviewAgent.Answer(phase, "MULTIPLE_CHOICE", asked, picked, null, assessmentService.earned(q, g) > 0));
                }
                default -> {
                    // coding answers are graded by the sandbox, not reviewed here
                }
            }
        }
        JobOpening job = invite.getApplication().getJob();
        return new ReviewWork(new InterviewAgent.Job(job.getTitle(), job.getDescription()), answers);
    }

    /** Records the review and the app's score, if still pending, and notes it in the team chat. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeReview(UUID inviteId, boolean usesAi, String model, InterviewAgent.Review review) {
        AssessmentInvite invite = inviteRepository.findById(inviteId).orElse(null);
        if (invite == null || invite.getEvaluation() != AssessmentInvite.Evaluation.PENDING) {
            return;
        }
        Integer marked = invite.getMaxScore() == null || invite.getMaxScore() == 0 ? null : invite.getPercent();
        Integer score = InterviewReviews.score(review, marked);
        invite.setEvaluationJson(reviews.write(new InterviewReviews.Stored(usesAi, model, review)));
        invite.setAiScore(score);
        invite.setEvaluation(AssessmentInvite.Evaluation.DONE);
        invite.setEvaluatedAt(Instant.now());
        log.info("Interview {} reviewed ({}): score {}", inviteId, usesAi ? "AI" : "rules", score);
        teamNote(invite, (usesAi ? "AI review" : "Basic review (rules, not AI)") + " of "
                + invite.getApplication().getCandidate().getName() + "'s interview: " + (score == null ? "no score" : score + "/100")
                + ". " + (review.summary() == null ? "" : review.summary().strip()) + " Advisory only — watch the answers before deciding.");
    }

    /** The agent couldn't review now: try again later, or give up after many attempts and tell the team. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reviewFailed(UUID inviteId, String reason) {
        AssessmentInvite invite = inviteRepository.findById(inviteId).orElse(null);
        if (invite == null || invite.getEvaluation() != AssessmentInvite.Evaluation.PENDING) {
            return;
        }
        invite.setEvaluationAttempts(invite.getEvaluationAttempts() + 1);
        if (invite.getEvaluationAttempts() >= MAX_REVIEW_ATTEMPTS) {
            invite.setEvaluation(AssessmentInvite.Evaluation.FAILED);
            teamNote(invite, "The AI couldn't review " + invite.getApplication().getCandidate().getName() + "'s interview (" + reason
                    + "). Open it under the opening's AI interview and click Review again.");
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> pendingReviews() {
        return inviteRepository.findByEvaluation(AssessmentInvite.Evaluation.PENDING).stream().map(AssessmentInvite::getId).toList();
    }

    /** Staff: run the review again (after the AI was down, or for a fresh look). */
    @Transactional
    public void reviewAgain(AppUser actor, UUID inviteId) {
        accessPolicy.require(actor, Capability.MANAGE_JOBS);
        AssessmentInvite invite = inviteRepository.findById(inviteId).orElseThrow(() -> new NotFoundException("Interview not found"));
        if (invite.getAssessment().getCategory() != Assessment.Category.INTERVIEW || invite.getStatus() != AssessmentInvite.Status.SUBMITTED) {
            throw new IllegalArgumentException("Only a submitted AI interview can be reviewed again");
        }
        invite.setEvaluation(AssessmentInvite.Evaluation.PENDING);
        invite.setEvaluationAttempts(0);
        auditService.record(actor, AuditAction.INTERVIEW_REVIEWED_AGAIN, "AssessmentInvite", inviteId, Map.of());
        events.publishEvent(new InterviewReviewRequested(inviteId));
    }

    // ---- the ranking ----

    /** The opening's AI interviews, best first: reviewed by score, then waiting for review, then not yet taken. */
    @Transactional
    public JobInterviews ranking(AppUser actor, UUID jobId) {
        accessPolicy.require(actor, Capability.VIEW_CANDIDATES);
        List<Application> apps = applicationRepository.findByJobIdOrderByCandidateNameAsc(jobId);
        if (apps.isEmpty()) {
            return new JobInterviews(List.of(), 0, 0, 0, 0);
        }
        Map<UUID, AssessmentInvite> latest = new HashMap<>();
        for (AssessmentInvite i : inviteRepository.findByApplicationIdInOrderBySentAtDesc(apps.stream().map(Application::getId).toList())) {
            if (i.getAssessment().getCategory() == Assessment.Category.INTERVIEW && i.getStatus() != AssessmentInvite.Status.CANCELLED) {
                inviteService.finalise(i);
                latest.putIfAbsent(i.getApplication().getId(), i);
            }
        }
        Map<UUID, Long> videos = videoRepository.findByInviteIdIn(latest.values().stream().map(AssessmentInvite::getId).toList()).stream()
                .collect(Collectors.groupingBy(InterviewVideo::getInviteId, Collectors.counting()));
        List<RankedCandidate> rows = new ArrayList<>();
        for (AssessmentInvite i : latest.values()) {
            InterviewReviews.Stored stored = reviews.read(i);
            InterviewAgent.Review r = stored == null ? null : stored.review();
            Integer marked = i.getStatus() != AssessmentInvite.Status.SUBMITTED || i.getMaxScore() == null || i.getMaxScore() == 0
                    ? null : i.getPercent();
            rows.add(new RankedCandidate(i.getId(), i.getApplication().getId(), i.getApplication().getCandidate().getName(),
                    i.getApplication().getStage(), i.getStatus(), i.getEvaluation(), stored != null && stored.usesAi(), i.getAiScore(), marked,
                    InterviewReviews.ratings(r), r == null ? null : r.summary(), r == null ? null : blankToNull(r.noticePeriod()),
                    r == null ? null : blankToNull(r.expectedSalary()), i.getSentAt(), i.getSubmittedAt(),
                    videos.getOrDefault(i.getId(), 0L).intValue()));
        }
        rows.sort(Comparator.comparingInt(AiInterviewService::rank)
                .thenComparing(r -> r.aiScore() == null ? -1 : r.aiScore(), Comparator.reverseOrder())
                .thenComparing(RankedCandidate::candidateName, String.CASE_INSENSITIVE_ORDER));
        int started = (int) rows.stream().filter(r -> r.status() == AssessmentInvite.Status.STARTED).count();
        int submitted = (int) rows.stream().filter(r -> r.status() == AssessmentInvite.Status.SUBMITTED).count();
        int reviewed = (int) rows.stream().filter(r -> r.evaluation() == AssessmentInvite.Evaluation.DONE).count();
        return new JobInterviews(rows, rows.size(), started, submitted, reviewed);
    }

    private static int rank(RankedCandidate r) {
        if (r.status() == AssessmentInvite.Status.SUBMITTED) {
            return r.evaluation() == AssessmentInvite.Evaluation.DONE ? 0 : 1;
        }
        return switch (r.status()) {
            case STARTED -> 2;
            case SENT -> 3;
            default -> 4;
        };
    }

    // ---- helpers ----

    private void teamNote(AssessmentInvite invite, String body) {
        messageRepository.save(Message.builder()
                .application(invite.getApplication())
                .channel(MessageChannel.TEAM)
                .authorType(MessageAuthorType.STAFF)
                .authorEmail(AssessmentInviteService.SYSTEM_AUTHOR)
                .authorName("CodeWalnut ATS")
                .body(body)
                .emailed(false)
                .build());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
