package com.codewalnut.ats.service;

import com.codewalnut.ats.client.CalendarException;
import com.codewalnut.ats.client.MailClient;
import com.codewalnut.ats.client.WhatsAppClient;
import com.codewalnut.ats.config.WhatsAppProperties;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.ApplicationEvent;
import com.codewalnut.ats.domain.ApplicationEventType;
import com.codewalnut.ats.domain.AuditAction;
import com.codewalnut.ats.domain.CandidateAccount;
import com.codewalnut.ats.domain.ClientContact;
import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.dto.MessageDtos.CandidateMessageResponse;
import com.codewalnut.ats.dto.MessageDtos.InboxItem;
import com.codewalnut.ats.dto.MessageDtos.MessageResponse;
import com.codewalnut.ats.dto.MessageDtos.PostMessageRequest;
import com.codewalnut.ats.dto.MessageDtos.WhatsAppStatusResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.codewalnut.ats.repository.ApplicationEventRepository;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.MessageRepository;
import com.codewalnut.ats.security.AccessPolicy;
import com.codewalnut.ats.security.Capability;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Conversations per application: with the candidate (in-app, optionally emailed from the
 * sender's Gmail) and within the team. See docs/features/communication.md and ADR-0006.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    /** Candidate messages allowed per application per hour, to stop floods. */
    static final int CANDIDATE_HOURLY_LIMIT = 20;

    private final ApplicationRepository applicationRepository;
    private final MessageRepository messageRepository;
    private final ApplicationEventRepository eventRepository;
    private final MailClient mailClient;
    private final WhatsAppClient whatsAppClient;
    private final WhatsAppProperties whatsAppProperties;
    private final ClientPortalService clientPortalService;
    private final AccessPolicy accessPolicy;
    private final AuditService auditService;

    // ---- staff ----

    @Transactional(readOnly = true)
    public List<MessageResponse> thread(AppUser actor, UUID applicationId, MessageChannel channel) {
        accessPolicy.require(actor, Capability.MESSAGE_CANDIDATES);
        application(applicationId);
        return messageRepository.findByApplicationIdAndChannelOrderByCreatedAtAsc(applicationId, channel).stream()
                .map(MessageResponse::from)
                .toList();
    }

    /** portalUrl: where the candidate signs in to read and reply; included in emails. */
    @Transactional
    public MessageResponse post(AppUser actor, UUID applicationId, PostMessageRequest request, String portalUrl) {
        accessPolicy.require(actor, Capability.MESSAGE_CANDIDATES);
        Application application = application(applicationId);
        if (request.channel() == MessageChannel.CLIENT && application.getJob().getClient() == null) {
            throw new IllegalArgumentException("channel: this opening is internal, so there is no client to message");
        }
        String body = request.body().strip();
        boolean toCandidate = request.channel() == MessageChannel.CANDIDATE;
        boolean email = request.sendEmail() && toCandidate;
        boolean whatsapp = request.sendWhatsApp() && toCandidate;
        String phone = null;
        if (whatsapp) {
            phone = PhoneNumbers.forWhatsApp(application.getCandidate().getPhone(), whatsAppProperties.defaultCountryCode());
            if (phone == null) {
                throw new IllegalArgumentException("phone: add the candidate's mobile number first (with country code if outside India)");
            }
        }
        // Email first: if it fails nothing has gone out yet, so nothing is saved.
        String subject = null;
        String providerId = null;
        if (email) {
            String to = application.getCandidate().getEmail();
            if (!StringUtils.hasText(to)) {
                throw new IllegalArgumentException("email: add the candidate's email address first");
            }
            subject = StringUtils.hasText(request.subject())
                    ? request.subject().strip()
                    : "Your application for " + application.getJob().getTitle() + " at CodeWalnut";
            providerId = mailClient.send(new MailClient.Email(to, subject, emailBody(body, to, portalUrl)));
        }
        List<String> warnings = new ArrayList<>();
        String whatsappStatus = null;
        String whatsappId = null;
        String whatsappLink = null;
        if (whatsapp) {
            String text = body + "\n\nReply here or see your application at " + portalUrl;
            if (whatsAppClient.apiEnabled()) {
                try {
                    whatsappId = whatsAppClient.send(new WhatsAppClient.Outgoing(phone,
                            firstName(application.getCandidate().getName()), application.getJob().getTitle(), text,
                            withinReplyWindow(applicationId)));
                    whatsappStatus = "SENT";
                } catch (CalendarException e) {
                    if (!email) {
                        throw e; // nothing went out
                    }
                    warnings.add("The email was sent, but WhatsApp failed: " + e.getMessage());
                }
            } else {
                // No Business API: the recruiter's browser opens WhatsApp with the text ready.
                whatsappStatus = "OPENED";
                whatsappLink = "https://wa.me/" + phone + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
            }
        }
        Message message = messageRepository.save(Message.builder()
                .application(application)
                .channel(request.channel())
                .authorType(MessageAuthorType.STAFF)
                .authorEmail(actor.getEmail())
                .authorName(actor.getName())
                .subject(subject)
                .body(body)
                .emailed(email)
                .emailMessageId(providerId)
                .whatsappStatus(whatsappStatus)
                .whatsappMessageId(whatsappId)
                .build());
        if (request.channel() != MessageChannel.TEAM) {
            application.setUpdatedAt(Instant.now());
        }
        if (email) {
            eventRepository.save(ApplicationEvent.builder()
                    .application(application)
                    .type(ApplicationEventType.EMAIL_SENT)
                    .note("Email: " + subject)
                    .actorEmail(actor.getEmail())
                    .build());
            // Never the content or the address: just that it happened.
            auditService.record(actor, AuditAction.EMAIL_SENT, "Message", message.getId(),
                    Map.of("applicationId", applicationId));
        }
        if (whatsappStatus != null) {
            eventRepository.save(ApplicationEvent.builder()
                    .application(application)
                    .type(ApplicationEventType.WHATSAPP_SENT)
                    .note(("SENT".equals(whatsappStatus) ? "WhatsApp sent: " : "WhatsApp opened to send: ") + preview(body))
                    .actorEmail(actor.getEmail())
                    .build());
            auditService.record(actor, AuditAction.WHATSAPP_SENT, "Message", message.getId(),
                    Map.of("applicationId", applicationId, "via", "SENT".equals(whatsappStatus) ? "api" : "click-to-chat"));
        }
        return MessageResponse.sent(message, whatsappLink, warnings);
    }

    public WhatsAppStatusResponse whatsAppStatus(AppUser actor) {
        accessPolicy.require(actor, Capability.MESSAGE_CANDIDATES);
        return new WhatsAppStatusResponse(whatsAppClient.apiEnabled(), whatsAppProperties.webhookEnabled());
    }

    /** WhatsApp allows free text only within 24 hours of the candidate's last WhatsApp message. */
    private boolean withinReplyWindow(UUID applicationId) {
        return messageRepository.findFirstByApplicationIdAndAuthorTypeAndWhatsappStatusOrderByCreatedAtDesc(
                        applicationId, MessageAuthorType.CANDIDATE, "RECEIVED")
                .map(m -> m.getCreatedAt().isAfter(Instant.now().minus(Duration.ofHours(23))))
                .orElse(false);
    }

    private static String firstName(String name) {
        String[] parts = name.strip().split("\\s+");
        return parts.length > 0 ? parts[0] : name;
    }

    /** Latest conversations with candidates and clients, those waiting for our reply first. */
    @Transactional(readOnly = true)
    public List<InboxItem> inbox(AppUser actor) {
        accessPolicy.require(actor, Capability.MESSAGE_CANDIDATES);
        Map<String, Message> latest = new LinkedHashMap<>();
        for (Message m : messageRepository.findByChannelInOrderByCreatedAtDesc(
                List.of(MessageChannel.CANDIDATE, MessageChannel.CLIENT), PageRequest.of(0, 1000))) {
            latest.putIfAbsent(m.getApplication().getId() + "/" + m.getChannel(), m);
        }
        List<InboxItem> items = new ArrayList<>();
        for (Message m : latest.values()) {
            Application a = m.getApplication();
            boolean external = m.getAuthorType() != MessageAuthorType.STAFF;
            String author = m.getAuthorName() != null ? m.getAuthorName() : m.getAuthorEmail();
            items.add(new InboxItem(a.getId(), a.getJob().getId(), a.getJob().getTitle(), a.getCandidate().getName(),
                    m.getChannel(), a.getJob().getClient() != null ? a.getJob().getClient().getName() : null,
                    author, external, preview(m.getBody()), m.getCreatedAt(), external));
        }
        items.sort(Comparator.comparing(InboxItem::awaitingReply).reversed()
                .thenComparing(InboxItem::lastAt, Comparator.reverseOrder()));
        return items.size() > 200 ? items.subList(0, 200) : items;
    }

    // ---- client contacts ----

    @Transactional(readOnly = true)
    public List<CandidateMessageResponse> clientThread(ClientContact contact, UUID applicationId) {
        Application application = clientPortalService.sharedApplication(contact, applicationId);
        return messageRepository.findByApplicationIdAndChannelOrderByCreatedAtAsc(application.getId(), MessageChannel.CLIENT)
                .stream()
                .map(m -> CandidateMessageResponse.forViewer(m, MessageAuthorType.CLIENT, contact.getEmail()))
                .toList();
    }

    @Transactional
    public CandidateMessageResponse clientPost(ClientContact contact, UUID applicationId, String text) {
        Application application = clientPortalService.sharedApplication(contact, applicationId);
        long recent = messageRepository.countByApplicationIdAndChannelAndAuthorTypeAndCreatedAtAfter(application.getId(),
                MessageChannel.CLIENT, MessageAuthorType.CLIENT, Instant.now().minus(Duration.ofHours(1)));
        if (recent >= CANDIDATE_HOURLY_LIMIT) {
            throw new IllegalArgumentException("You've sent a lot of messages in the last hour. Please wait a little.");
        }
        Message message = messageRepository.save(Message.builder()
                .application(application)
                .channel(MessageChannel.CLIENT)
                .authorType(MessageAuthorType.CLIENT)
                .authorEmail(contact.getEmail())
                .authorName(contact.getName() != null ? contact.getName() + " (" + contact.getClient().getName() + ")"
                        : contact.getClient().getName())
                .body(text.strip())
                .build());
        application.setUpdatedAt(Instant.now());
        return CandidateMessageResponse.forViewer(message, MessageAuthorType.CLIENT, contact.getEmail());
    }

    // ---- candidate ----

    @Transactional
    public List<CandidateMessageResponse> candidateThread(CandidateAccount account, UUID applicationId) {
        Application application = own(account, applicationId);
        if (!com.codewalnut.ats.security.ViewAs.active()) { // an admin viewing as them hasn't read it
            application.setCandidateReadAt(Instant.now());
        }
        return messageRepository.findByApplicationIdAndChannelOrderByCreatedAtAsc(applicationId, MessageChannel.CANDIDATE)
                .stream().map(CandidateMessageResponse::from).toList();
    }

    @Transactional
    public CandidateMessageResponse candidatePost(CandidateAccount account, UUID applicationId, String text) {
        Application application = own(account, applicationId);
        long recent = messageRepository.countByApplicationIdAndChannelAndAuthorTypeAndCreatedAtAfter(applicationId,
                MessageChannel.CANDIDATE, MessageAuthorType.CANDIDATE, Instant.now().minus(Duration.ofHours(1)));
        if (recent >= CANDIDATE_HOURLY_LIMIT) {
            throw new IllegalArgumentException("You've sent a lot of messages in the last hour. Please wait a little.");
        }
        Message message = messageRepository.save(Message.builder()
                .application(application)
                .channel(MessageChannel.CANDIDATE)
                .authorType(MessageAuthorType.CANDIDATE)
                .authorEmail(account.getEmail())
                .authorName(application.getCandidate().getName())
                .body(text.strip())
                .build());
        Instant now = Instant.now();
        application.setCandidateReadAt(now);
        application.setUpdatedAt(now);
        return CandidateMessageResponse.from(message);
    }

    /** New messages from CodeWalnut since the candidate last looked. */
    @Transactional(readOnly = true)
    public long unreadForCandidate(Application application) {
        return application.getCandidateReadAt() == null
                ? messageRepository.countByApplicationIdAndChannelAndAuthorType(application.getId(),
                        MessageChannel.CANDIDATE, MessageAuthorType.STAFF)
                : messageRepository.countByApplicationIdAndChannelAndAuthorTypeAndCreatedAtAfter(application.getId(),
                        MessageChannel.CANDIDATE, MessageAuthorType.STAFF, application.getCandidateReadAt());
    }

    // ---- helpers ----

    /** A candidate can only reach their own applications; anything else is "not found". */
    private Application own(CandidateAccount account, UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .filter(a -> a.getCandidate().getEmail() != null
                        && a.getCandidate().getEmail().equalsIgnoreCase(account.getEmail()))
                .orElseThrow(() -> new NotFoundException("Application not found"));
    }

    private Application application(UUID id) {
        return applicationRepository.findById(id).orElseThrow(() -> new NotFoundException("Application not found"));
    }

    static String emailBody(String body, String to, String portalUrl) {
        return body + "\n\n--\nYou can reply to this email, or sign in at " + portalUrl
                + " with your Google account (" + to + ") to see your messages and application status.";
    }

    private static String preview(String body) {
        String flat = body.replaceAll("\\s+", " ").strip();
        return flat.length() > 140 ? flat.substring(0, 139) + "…" : flat;
    }
}
