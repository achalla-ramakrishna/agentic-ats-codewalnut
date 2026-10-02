package com.codewalnut.ats.service;

import com.codewalnut.ats.config.WhatsAppProperties;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.CandidateRepository;
import com.codewalnut.ats.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WhatsApp Business API webhook: candidate replies land in the candidate conversation of their
 * latest application, and delivery receipts update the status of our messages. Every call is
 * signature-checked and idempotent by WhatsApp message id (ADR-0008).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppInboundService {

    private static final Set<String> STATUSES = Set.of("sent", "delivered", "read", "failed");

    private final WhatsAppProperties properties;
    private final CandidateRepository candidateRepository;
    private final ApplicationRepository applicationRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public boolean enabled() {
        return properties.webhookEnabled();
    }

    /** Meta's subscription check: echo the challenge only when the verify token matches. */
    public Optional<String> verify(String mode, String token, String challenge) {
        if (!enabled() || !"subscribe".equals(mode) || token == null || challenge == null) {
            return Optional.empty();
        }
        boolean ok = MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                properties.verifyToken().getBytes(StandardCharsets.UTF_8));
        return ok ? Optional.of(challenge) : Optional.empty();
    }

    /** X-Hub-Signature-256 is "sha256=" + hex HMAC of the raw body with the app secret. */
    public boolean signatureValid(byte[] body, String header) {
        if (!enabled() || header == null || !header.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] given = HexFormat.of().parseHex(header.substring(7).toLowerCase(Locale.ROOT));
            return MessageDigest.isEqual(expected, given);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            return false;
        }
    }

    @Transactional
    public void handle(byte[] body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                for (JsonNode status : value.path("statuses")) {
                    String state = status.path("status").asText("");
                    if (STATUSES.contains(state)) {
                        messageRepository.updateWhatsappStatus(status.path("id").asText(), state.toUpperCase(Locale.ROOT));
                    }
                }
                for (JsonNode incoming : value.path("messages")) {
                    receive(incoming);
                }
            }
        }
    }

    private void receive(JsonNode incoming) {
        String id = incoming.path("id").asText(null);
        String from = incoming.path("from").asText("").replaceAll("\\D", "");
        if (id == null || from.isEmpty() || messageRepository.existsByWhatsappMessageId(id)) {
            return;
        }
        Optional<Candidate> candidate = findCandidate(from);
        if (candidate.isEmpty()) {
            log.info("WhatsApp message from a number that isn't a candidate; ignored");
            return;
        }
        List<Application> applications = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidate.get().getId());
        Optional<Application> latest = applications.stream().max(Comparator.comparing(Application::getUpdatedAt));
        if (latest.isEmpty()) {
            return;
        }
        String type = incoming.path("type").asText("");
        String text = "text".equals(type)
                ? incoming.path("text").path("body").asText("")
                : "[Sent a " + type + " on WhatsApp. Files sent on WhatsApp don't reach the ATS: ask them to upload "
                        + "documents on their candidate page.]";
        if (text.isBlank()) {
            return;
        }
        Application application = latest.get();
        Candidate c = candidate.get();
        messageRepository.save(Message.builder()
                .application(application)
                .channel(MessageChannel.CANDIDATE)
                .authorType(MessageAuthorType.CANDIDATE)
                .authorEmail(c.getEmail() != null ? c.getEmail() : "whatsapp")
                .authorName(c.getName())
                .body(text.length() > 10_000 ? text.substring(0, 10_000) : text)
                .whatsappStatus("RECEIVED")
                .whatsappMessageId(id)
                .build());
        application.setUpdatedAt(Instant.now());
    }

    /** Numbers are stored as typed (e.g. 9000011111 or +919000011111); WhatsApp sends 919000011111. */
    private Optional<Candidate> findCandidate(String from) {
        String cc = properties.defaultCountryCode();
        List<String> forms = from.startsWith(cc) && from.length() == cc.length() + 10
                ? List.of(from, "+" + from, from.substring(cc.length()), "0" + from.substring(cc.length()))
                : List.of(from, "+" + from);
        for (String form : forms) {
            List<Candidate> found = candidateRepository.findByPhone(form);
            if (!found.isEmpty()) {
                return Optional.of(found.get(0));
            }
        }
        return Optional.empty();
    }
}
