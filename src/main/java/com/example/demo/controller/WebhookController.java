package com.example.demo.controller;

import com.example.demo.entity.WebhookDelivery;
import com.example.demo.repository.WebhookDeliveryRepository;
import com.example.demo.service.HmacSignatureService;
import com.example.demo.service.WebhookProcessingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final HmacSignatureService hmacSignatureService;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookProcessingService processingService;
    private final ObjectMapper objectMapper;

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryGuid,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawPayload
    ) {
        log.info("Received GitHub Webhook: Event='{}', Delivery='{}'", eventType, deliveryGuid);

        // 1. Validate Delivery GUID presence
        if (deliveryGuid == null || deliveryGuid.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing X-GitHub-Delivery header");
        }

        // 2. Cryptographic Signature Verification (HMAC-SHA256)
        if (!hmacSignatureService.isValidSignature(rawPayload, signature)) {
            log.warn("Invalid HMAC signature detected for delivery: {}", deliveryGuid);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid HMAC signature");
        }

        // 3. Idempotency Check (Prevent duplicate/replayed processing)
        if (deliveryRepository.existsByDeliveryGuid(deliveryGuid)) {
            log.warn("Duplicate delivery detected: {}. Returning fast 200 OK (idempotent no-op).", deliveryGuid);
            return ResponseEntity.ok("Duplicate delivery acknowledged; already recorded.");
        }

        // 4. Extract repository and action metadata for initial record
        String repoName = "unknown";
        String action = null;
        String sender = null;
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            repoName = root.path("repository").path("full_name").asText("unknown");
            action = root.path("action").asText(null);
            sender = root.path("sender").path("login").asText(null);
        } catch (Exception ignored) {}

        // 5. Atomically Record Webhook Delivery
        WebhookDelivery delivery = WebhookDelivery.builder()
                .deliveryGuid(deliveryGuid)
                .eventType(eventType != null ? eventType : "unknown")
                .action(action)
                .repository(repoName)
                .senderLogin(sender)
                .status(WebhookDelivery.DeliveryStatus.RECEIVED)
                .payloadJson(rawPayload)
                .receivedAt(LocalDateTime.now())
                .build();
        deliveryRepository.save(delivery);

        // 6. Fast-ACK: Dispatch asynchronously to worker, return 202 Accepted to GitHub (< 100ms)
        processingService.processEventAsync(deliveryGuid, eventType, rawPayload);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Delivery accepted and scheduled for processing.");
    }
}
