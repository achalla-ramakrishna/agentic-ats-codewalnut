package com.codewalnut.ats.service;

import com.codewalnut.ats.client.AskClient.ToolSpec;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.AssessmentInvite;
import com.codewalnut.ats.domain.DocumentKind;
import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.AskDtos.ProposedAction;
import com.codewalnut.ats.dto.AssessmentDtos.RemindRequest;
import com.codewalnut.ats.dto.AssessmentDtos.SendResult;
import com.codewalnut.ats.dto.ClientDtos.ShareRequest;
import com.codewalnut.ats.dto.MessageDtos.MessageResponse;
import com.codewalnut.ats.dto.MessageDtos.PostMessageRequest;
import com.codewalnut.ats.dto.TrackerDtos.MoveStageRequest;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.AssessmentInviteRepository;
import com.codewalnut.ats.repository.CandidateDocumentRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Actions in Ask ATS (ASK-06…ASK-09, ADR-0023). The AI only proposes: each proposal is checked here
 * (the candidate exists, the person may do it, the details make sense) and shown as a card. Nothing
 * changes until the person clicks Do it; then it runs through the same service as the screen, as
 * that person, so permissions, history and the audit log are the usual ones.
 */
@Service
@RequiredArgsConstructor
public class AskActionService {

    static final int MAX_PER_ANSWER = 50;

    static final List<String> TYPES = List.of("MOVE_STAGE", "ADD_NOTE", "LOG_CONTACT", "REMIND_TEST", "MESSAGE", "SHARE_WITH_CLIENT");

    private final ApplicationRepository applicationRepository;
    private final AssessmentInviteRepository inviteRepository;
    private final CandidateDocumentRepository documentRepository;
    private final TrackerService tracker;
    private final WorkflowService workflow;
    private final AssessmentInviteService tests;
    private final MessageService messages;
    private final ClientShareService shares;
    private final AccessPolicy accessPolicy;

    static final ToolSpec TOOL = new ToolSpec("propose_actions",
            "Propose changes for the person to confirm. Nothing happens until they click Do it on each card, so never say "
                    + "something was done. Types: MOVE_STAGE (stage; note required for REJECTED: their reason), "
                    + "ADD_NOTE (note), LOG_CONTACT (how, note: a call or message made outside the app), REMIND_TEST (a test "
                    + "they haven't started; sendEmail and/or sendWhatsApp), MESSAGE (to the candidate: body, subject for "
                    + "email; sendEmail and/or sendWhatsApp; neither means a message on their candidate page; write the full "
                    + "message, signed by the person asking), SHARE_WITH_CLIENT (includeProfile, includeContact, "
                    + "includeCodeWalnutResume, note). Use applicationIds from the lookups.",
            Map.of("actions", Map.of("type", "array", "description", "One entry per candidate and action", "items", Map.of(
                    "type", "object",
                    "properties", Map.ofEntries(
                            Map.entry("type", Map.of("type", "string", "enum", TYPES)),
                            Map.entry("applicationId", Map.of("type", "string")),
                            Map.entry("stage", Map.of("type", "string", "enum", Stage.inUse().stream().map(Enum::name).toList())),
                            Map.entry("note", Map.of("type", "string")),
                            Map.entry("how", Map.of("type", "string", "enum", List.of("CALL", "WHATSAPP", "EMAIL", "MEETING", "OTHER"))),
                            Map.entry("sendEmail", Map.of("type", "boolean")),
                            Map.entry("sendWhatsApp", Map.of("type", "boolean")),
                            Map.entry("subject", Map.of("type", "string")),
                            Map.entry("body", Map.of("type", "string")),
                            Map.entry("includeProfile", Map.of("type", "boolean")),
                            Map.entry("includeContact", Map.of("type", "boolean")),
                            Map.entry("includeCodeWalnutResume", Map.of("type", "boolean"))),
                    "required", List.of("type", "applicationId")))),
            List.of("actions"));

    /** Checks each proposal; good ones are added to proposed, and the reply tells the AI what was accepted or refused. */
    public Map<String, Object> propose(AppUser actor, Map<String, Object> input, List<ProposedAction> proposed) {
        Object raw = input.get("actions");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("actions: give at least one action");
        }
        List<Map<String, Object>> accepted = new ArrayList<>();
        List<Map<String, Object>> refused = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> a = (Map<String, Object>) m;
            if (proposed.size() >= MAX_PER_ANSWER) {
                refused.add(Map.of("action", String.valueOf(a.get("type")), "reason", "Too many actions in one answer (max " + MAX_PER_ANSWER + ")"));
                continue;
            }
            try {
                ProposedAction action = check(actor, a);
                proposed.add(action);
                accepted.add(Map.of("id", action.id(), "summary", action.summary()));
            } catch (RuntimeException e) {
                refused.add(Map.of("action", String.valueOf(a.get("type")), "applicationId", String.valueOf(a.get("applicationId")),
                        "reason", e.getMessage() == null ? "Not possible" : e.getMessage()));
            }
        }
        return Map.of("proposed", accepted, "refused", refused,
                "note", "These are shown as cards. Nothing is done until the person clicks Do it.");
    }

    ProposedAction check(AppUser actor, Map<String, Object> a) {
        String type = text(a, "type") == null ? "" : text(a, "type").toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown action type");
        }
        allow(actor, Capability.VIEW_CANDIDATES, "see candidates");
        UUID applicationId;
        try {
            applicationId = UUID.fromString(String.valueOf(a.get("applicationId")));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("applicationId must come from a lookup");
        }
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("No candidate with that applicationId"));
        String name = app.getCandidate().getName();
        String job = app.getJob().getTitle();
        Map<String, String> params = new LinkedHashMap<>();
        String summary;
        switch (type) {
            case "MOVE_STAGE" -> {
                allow(actor, Capability.MANAGE_JOBS, "move candidates");
                Stage to;
                try {
                    to = Stage.valueOf(String.valueOf(a.get("stage")).toUpperCase(Locale.ROOT)).current();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("stage: not a stage");
                }
                if (to == app.getStage()) {
                    throw new IllegalArgumentException(name + " is already at " + to.getLabel());
                }
                String note = text(a, "note");
                if (to.requiresReason() && note == null) {
                    throw new IllegalArgumentException("Moving to " + to.getLabel() + " needs a reason from the person asking");
                }
                params.put("stage", to.name());
                put(params, "note", note);
                summary = "Move " + name + " (" + job + "): " + app.getStage().getLabel() + " → " + to.getLabel()
                        + (note == null ? "" : ". Reason: " + note);
            }
            case "ADD_NOTE" -> {
                allow(actor, Capability.MANAGE_JOBS, "add notes");
                String note = required(a, "note");
                params.put("note", note);
                summary = "Note on " + name + " (" + job + "): " + note;
            }
            case "LOG_CONTACT" -> {
                allow(actor, Capability.MESSAGE_CANDIDATES, "log contact");
                String how = text(a, "how") == null ? "CALL" : text(a, "how").toUpperCase(Locale.ROOT);
                String label = WorkflowService.HOW.get(how);
                if (label == null) {
                    throw new IllegalArgumentException("how: call, WhatsApp, email, meeting or other");
                }
                params.put("how", how);
                put(params, "note", text(a, "note"));
                summary = "Log " + label.toLowerCase(Locale.ROOT) + " with " + name + (text(a, "note") == null ? "" : ": " + text(a, "note"));
            }
            case "REMIND_TEST" -> {
                allow(actor, Capability.MANAGE_JOBS, "remind about tests");
                allow(actor, Capability.MESSAGE_CANDIDATES, "message candidates");
                AssessmentInvite invite = inviteRepository.findByApplicationIdOrderBySentAtDesc(applicationId).stream()
                        .filter(i -> i.getStatus() == AssessmentInvite.Status.SENT || i.getStatus() == AssessmentInvite.Status.EXPIRED)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(name + " has no test waiting to be started"));
                boolean whatsapp = bool(a, "sendWhatsApp", false) && StringUtils.hasText(app.getCandidate().getPhone());
                boolean email = bool(a, "sendEmail", !whatsapp) && StringUtils.hasText(app.getCandidate().getEmail());
                if (!email && !whatsapp) {
                    throw new IllegalArgumentException(name + " has no email or mobile number to remind");
                }
                params.put("inviteId", invite.getId().toString());
                params.put("sendEmail", String.valueOf(email));
                params.put("sendWhatsApp", String.valueOf(whatsapp));
                summary = "Remind " + name + " about \"" + invite.getAssessment().getTitle() + "\" by " + channels(email, whatsapp);
            }
            case "MESSAGE" -> {
                allow(actor, Capability.MESSAGE_CANDIDATES, "message candidates");
                String body = required(a, "body");
                boolean email = bool(a, "sendEmail", false);
                boolean whatsapp = bool(a, "sendWhatsApp", false);
                if (email && !StringUtils.hasText(app.getCandidate().getEmail())) {
                    throw new IllegalArgumentException(name + " has no email address");
                }
                if (whatsapp && !StringUtils.hasText(app.getCandidate().getPhone())) {
                    throw new IllegalArgumentException(name + " has no mobile number");
                }
                params.put("body", body);
                put(params, "subject", email ? text(a, "subject") : null);
                params.put("sendEmail", String.valueOf(email));
                params.put("sendWhatsApp", String.valueOf(whatsapp));
                summary = (email || whatsapp ? channels(email, whatsapp) + " to " : "Message on the candidate page to ") + name
                        + (email && text(a, "subject") != null ? ": " + text(a, "subject") : "");
            }
            case "SHARE_WITH_CLIENT" -> {
                allow(actor, Capability.SHARE_WITH_CLIENTS, "share with clients");
                if (app.getJob().getClient() == null) {
                    throw new IllegalArgumentException(job + " is an internal opening; there is no client");
                }
                boolean profile = bool(a, "includeProfile", true);
                boolean contact = bool(a, "includeContact", false);
                String resume = null;
                if (bool(a, "includeCodeWalnutResume", true)) {
                    resume = documentRepository.findByCandidateIdOrderByUploadedAtDesc(app.getCandidate().getId()).stream()
                            .filter(d -> d.getKind() == DocumentKind.CODEWALNUT_RESUME)
                            .map(d -> d.getId().toString()).findFirst().orElse(null);
                }
                params.put("includeProfile", String.valueOf(profile));
                params.put("includeContact", String.valueOf(contact));
                put(params, "documentId", resume);
                put(params, "note", text(a, "note"));
                List<String> what = new ArrayList<>();
                if (profile) {
                    what.add("profile");
                }
                if (contact) {
                    what.add("contact details");
                }
                if (resume != null) {
                    what.add("CodeWalnut résumé");
                }
                summary = "Share " + name + " with " + app.getJob().getClient().getName()
                        + (what.isEmpty() ? "" : " (" + String.join(", ", what) + ")");
            }
            default -> throw new IllegalArgumentException("Unknown action type");
        }
        return new ProposedAction(UUID.randomUUID().toString(), type, applicationId, app.getJob().getId(), name, job, summary,
                params, "PENDING", null, null, null);
    }

    /** What happened, and a WhatsApp link to open when WhatsApp goes by click-to-chat. */
    public record Done(String result, String whatsappLink) {}

    /** Runs a confirmed action as the person, through the usual service. subject and body: their edits to a message. */
    public Done execute(AppUser actor, ProposedAction action, String subject, String body, String portalUrl) {
        Map<String, String> p = action.params();
        UUID id = action.applicationId();
        return switch (action.type()) {
            case "MOVE_STAGE" -> {
                Stage to = Stage.valueOf(p.get("stage"));
                tracker.moveStage(actor, id, new MoveStageRequest(to, p.get("note")));
                yield new Done("Moved to " + to.getLabel(), null);
            }
            case "ADD_NOTE" -> {
                tracker.addNote(actor, id, p.get("note"));
                yield new Done("Note added", null);
            }
            case "LOG_CONTACT" -> {
                workflow.logContact(actor, id, p.get("how"), p.get("note"));
                yield new Done("Logged", null);
            }
            case "REMIND_TEST" -> {
                SendResult sent = tests.remind(actor, UUID.fromString(p.get("inviteId")),
                        new RemindRequest(Boolean.parseBoolean(p.get("sendEmail")), Boolean.parseBoolean(p.get("sendWhatsApp"))), portalUrl);
                yield sentResult("Reminder sent", sent.message());
            }
            case "MESSAGE" -> {
                String text = StringUtils.hasText(body) ? body.strip() : p.get("body");
                String subj = StringUtils.hasText(subject) ? subject.strip() : p.get("subject");
                MessageResponse sent = messages.post(actor, id, new PostMessageRequest(MessageChannel.CANDIDATE, text, subj,
                        Boolean.parseBoolean(p.get("sendEmail")), Boolean.parseBoolean(p.get("sendWhatsApp"))), portalUrl);
                yield sentResult("Sent", sent);
            }
            case "SHARE_WITH_CLIENT" -> {
                List<UUID> docs = p.get("documentId") == null ? List.of() : List.of(UUID.fromString(p.get("documentId")));
                shares.share(actor, id, new ShareRequest(Boolean.parseBoolean(p.get("includeContact")),
                        Boolean.parseBoolean(p.get("includeProfile")), docs, p.get("note")));
                yield new Done("Shared", null);
            }
            default -> throw new IllegalArgumentException("Unknown action type");
        };
    }

    private static Done sentResult(String done, MessageResponse message) {
        if (message != null && message.whatsappLink() != null) {
            return new Done(done + "; WhatsApp opened with the message ready: press Send there", message.whatsappLink());
        }
        String warnings = message == null || message.warnings() == null || message.warnings().isEmpty() ? ""
                : " (" + String.join(" ", message.warnings()) + ")";
        return new Done(done + warnings, null);
    }

    private static String channels(boolean email, boolean whatsapp) {
        return email && whatsapp ? "Email + WhatsApp" : email ? "Email" : "WhatsApp";
    }

    private void allow(AppUser actor, Capability capability, String what) {
        if (!accessPolicy.has(actor, capability)) {
            throw new IllegalArgumentException("Not allowed: this person can't " + what);
        }
    }

    private static String text(Map<String, Object> a, String key) {
        Object v = a.get(key);
        return v == null || !StringUtils.hasText(v.toString()) ? null : v.toString().strip();
    }

    private static String required(Map<String, Object> a, String key) {
        String v = text(a, key);
        if (v == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v;
    }

    private static boolean bool(Map<String, Object> a, String key, boolean fallback) {
        Object v = a.get(key);
        return v == null ? fallback : Boolean.parseBoolean(v.toString());
    }

    private static void put(Map<String, String> params, String key, String value) {
        if (value != null) {
            params.put(key, value);
        }
    }
}
