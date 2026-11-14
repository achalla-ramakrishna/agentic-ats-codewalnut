package com.codewalnut.ats.service;

import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An opening's candidate contacts as a file (TRK-…): vCard for a phone's Contacts app (open it on an iPhone →
 * Add all contacts) or CSV for a spreadsheet. Only people who may contact candidates; every export is audited.
 */
@Service
@RequiredArgsConstructor
public class ContactExportService {

    public enum Format { VCF, CSV }

    public record ExportFile(String filename, String contentType, byte[] content, int people) {}

    private static final Pattern PHONE_SPLIT = Pattern.compile("[,;/|]");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Kolkata"));

    private final ApplicationRepository applicationRepository;
    private final JobOpeningRepository jobRepository;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    /** applicationIds: who to include (the people on screen or the ones ticked); empty means everyone in the opening. */
    @Transactional(readOnly = true)
    public ExportFile export(AppUser actor, UUID jobId, Format format, Collection<UUID> applicationIds) {
        accessPolicy.require(actor, Capability.MESSAGE_CANDIDATES);
        JobOpening job = jobRepository.findById(jobId).orElseThrow(() -> new NotFoundException("Opening not found"));
        Set<UUID> wanted = applicationIds == null ? Set.of() : new HashSet<>(applicationIds);
        List<Application> apps = applicationRepository.findByJobIdOrderByCandidateNameAsc(jobId).stream()
                .filter(a -> wanted.isEmpty() || wanted.contains(a.getId()))
                .toList();
        if (apps.isEmpty()) {
            throw new IllegalArgumentException("applicationIds: nobody to export");
        }
        String base = slug(job.getTitle()) + "-contacts";
        ExportFile file = format == Format.VCF
                ? new ExportFile(base + ".vcf", "text/vcard; charset=utf-8", vcards(job, apps).getBytes(StandardCharsets.UTF_8), apps.size())
                : new ExportFile(base + ".csv", "text/csv; charset=utf-8", csv(job, apps), apps.size());
        auditService.record(actor, AuditAction.CANDIDATE_CONTACTS_EXPORTED, "JobOpening", jobId,
                Map.of("format", format.name(), "people", apps.size()));
        return file;
    }

    // ---- vCard 3.0 (what iPhone and Android Contacts import) ----

    static String vcards(JobOpening job, List<Application> apps) {
        StringBuilder out = new StringBuilder();
        for (Application a : apps) {
            Candidate c = a.getCandidate();
            String name = c.getName() == null || c.getName().isBlank() ? c.getEmail() : c.getName().strip();
            String[] parts = name.split("\\s+");
            String last = parts.length > 1 ? parts[parts.length - 1] : "";
            String first = parts.length > 1 ? String.join(" ", Arrays.copyOf(parts, parts.length - 1)) : name;
            out.append("BEGIN:VCARD\r\nVERSION:3.0\r\n");
            out.append("N:").append(esc(last)).append(';').append(esc(first)).append(";;;\r\n");
            out.append("FN:").append(esc(name)).append("\r\n");
            // Shown under the name and searchable in Contacts, e.g. "Candidate – Senior Java Developer".
            out.append("ORG:").append(esc("Candidate – " + job.getTitle())).append("\r\n");
            for (String phone : phones(c.getPhone())) {
                out.append("TEL;TYPE=CELL:").append(esc(phone)).append("\r\n");
            }
            if (c.getEmail() != null && !c.getEmail().isBlank()) {
                out.append("EMAIL;TYPE=INTERNET:").append(esc(c.getEmail().strip())).append("\r\n");
            }
            if (c.getLinkedinUrl() != null && !c.getLinkedinUrl().isBlank()) {
                out.append("URL:").append(esc(c.getLinkedinUrl().strip())).append("\r\n");
            }
            out.append("NOTE:").append(esc("CodeWalnut ATS · " + job.getTitle() + " · " + a.getStage().getLabel())).append("\r\n");
            out.append("END:VCARD\r\n");
        }
        return out.toString();
    }

    /** vCard text escaping: backslash, comma, semicolon and new lines. */
    static String esc(String s) {
        return s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "");
    }

    /**
     * Phone numbers ready to call or WhatsApp: "98765 43210" → "+919876543210" (Indian mobiles get +91); several numbers
     * in one field ("98765 43210 / 91234 56789") become several. Anything unusual is kept as typed.
     */
    static List<String> phones(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String part : PHONE_SPLIT.split(raw)) {
            String p = part.strip();
            if (p.isEmpty()) {
                continue;
            }
            String digits = p.replaceAll("[^0-9]", "");
            String phone;
            if (p.startsWith("+") && digits.length() >= 8) {
                phone = "+" + digits;
            } else if (digits.length() == 10 && "6789".indexOf(digits.charAt(0)) >= 0) {
                phone = "+91" + digits;
            } else if (digits.length() == 11 && digits.startsWith("0") && "6789".indexOf(digits.charAt(1)) >= 0) {
                phone = "+91" + digits.substring(1);
            } else if (digits.length() == 12 && digits.startsWith("91")) {
                phone = "+" + digits;
            } else {
                phone = p;
            }
            if (!out.contains(phone)) {
                out.add(phone);
            }
        }
        return out;
    }

    // ---- CSV (Excel, Google Sheets, Google Contacts) ----

    static byte[] csv(JobOpening job, List<Application> apps) {
        StringBuilder out = new StringBuilder("\uFEFF"); // so Excel reads UTF-8 names correctly
        out.append("Name,Phone,Email,Stage,Opening,LinkedIn,Added\r\n");
        for (Application a : apps) {
            Candidate c = a.getCandidate();
            out.append(String.join(",",
                    cell(c.getName()),
                    cell(String.join(" / ", phones(c.getPhone()))),
                    cell(c.getEmail()),
                    cell(a.getStage().getLabel()),
                    cell(job.getTitle()),
                    cell(c.getLinkedinUrl()),
                    cell(a.getCreatedAt() == null ? "" : DAY.format(a.getCreatedAt()))));
            out.append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Quoted when needed; a cell a spreadsheet would run as a formula gets a leading apostrophe (phone numbers stay as they are). */
    static String cell(String v) {
        if (v == null) {
            return "";
        }
        String s = v.strip();
        if (!s.isEmpty() && "=@\t\r".indexOf(s.charAt(0)) >= 0
                || (!s.isEmpty() && "+-".indexOf(s.charAt(0)) >= 0 && !s.matches("[+-][0-9 ()/+-]*"))) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r") || s.startsWith("'")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String slug(String title) {
        String s = title == null ? "opening" : title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? "opening" : s;
    }
}
