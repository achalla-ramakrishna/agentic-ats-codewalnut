package com.codewalnut.ats.controller;

import com.codewalnut.ats.service.WhatsAppInboundService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta calls this for WhatsApp replies and delivery receipts. No session: every POST must carry
 * a valid X-Hub-Signature-256. Off (404) unless WHATSAPP_VERIFY_TOKEN and WHATSAPP_APP_SECRET are set.
 */
@RestController
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final WhatsAppInboundService inboundService;

    @GetMapping("/webhooks/whatsapp")
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!inboundService.enabled()) {
            return ResponseEntity.notFound().build();
        }
        return inboundService.verify(mode, token, challenge)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    @PostMapping("/webhooks/whatsapp")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] body,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) throws IOException {
        if (!inboundService.enabled()) {
            return ResponseEntity.notFound().build();
        }
        if (!inboundService.signatureValid(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        inboundService.handle(body);
        return ResponseEntity.ok().build();
    }
}
