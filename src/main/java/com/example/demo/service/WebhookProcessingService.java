package com.example.demo.service;

import com.example.demo.entity.AuditLog;
import com.example.demo.entity.AutomationRule;
import com.example.demo.entity.WebhookDelivery;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.repository.AutomationRuleRepository;
import com.example.demo.repository.WebhookDeliveryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookProcessingService {

    private final AutomationRuleRepository ruleRepository;
    private final AuditLogRepository auditLogRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final GitHubApiClient gitHubApiClient;
    private final SlackNotificationService slackNotificationService;
    private final GeminiAiService geminiAiService;
    private final ObjectMapper objectMapper;

    @Async
    public void processEventAsync(String deliveryGuid, String eventType, String rawPayload) {
        long startTime = System.currentTimeMillis();
        log.info("Processing event asynchronously: {} (delivery: {})", eventType, deliveryGuid);

        WebhookDelivery delivery = deliveryRepository.findByDeliveryGuid(deliveryGuid).orElse(null);
        if (delivery != null) {
            delivery.setStatus(WebhookDelivery.DeliveryStatus.PROCESSING);
            deliveryRepository.save(delivery);
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);

            // 1. Guard against infinite bot loops
            String senderType = root.path("sender").path("type").asText("");
            String senderLogin = root.path("sender").path("login").asText("");
            if ("Bot".equalsIgnoreCase(senderType) || senderLogin.endsWith("[bot]")) {
                log.info("Skipping event from bot user: {}", senderLogin);
                recordAudit(deliveryGuid, eventType, null, root.path("repository").path("full_name").asText(""),
                        "None", "Ignored bot event to prevent infinite loop", null,
                        AuditLog.ExecutionStatus.BOT_LOOP_PREVENTED, null, System.currentTimeMillis() - startTime);

                if (delivery != null) {
                    delivery.setStatus(WebhookDelivery.DeliveryStatus.PROCESSED);
                    deliveryRepository.save(delivery);
                }
                return;
            }

            // 2. Extract Event Specifics
            String action = root.path("action").asText("");
            String repoFullName = root.path("repository").path("full_name").asText("");
            String[] repoParts = repoFullName.split("/");
            String owner = repoParts.length > 0 ? repoParts[0] : "";
            String repoName = repoParts.length > 1 ? repoParts[1] : "";

            String title = "";
            String body = "";
            String htmlUrl = "";
            int itemNumber = 0;

            if ("issues".equalsIgnoreCase(eventType)) {
                JsonNode issue = root.path("issue");
                title = issue.path("title").asText("");
                body = issue.path("body").asText("");
                htmlUrl = issue.path("html_url").asText("");
                itemNumber = issue.path("number").asInt(0);
            } else if ("pull_request".equalsIgnoreCase(eventType)) {
                JsonNode pr = root.path("pull_request");
                title = pr.path("title").asText("");
                body = pr.path("body").asText("");
                htmlUrl = pr.path("html_url").asText("");
                itemNumber = pr.path("number").asInt(0);
            }

            // 3. Evaluate Configured Rules
            List<AutomationRule> activeRules = ruleRepository.findByActiveTrue();
            List<String> executedActionSummaries = new ArrayList<>();
            String matchedRuleName = null;
            String aiSummary = null;

            for (AutomationRule rule : activeRules) {
                if (!rule.getEventType().equalsIgnoreCase(eventType)) {
                    continue;
                }

                if (rule.getActionFilter() != null && !rule.getActionFilter().isBlank()
                        && !"ANY".equalsIgnoreCase(rule.getActionFilter())
                        && !rule.getActionFilter().equalsIgnoreCase(action)) {
                    continue;
                }

                // Check field matching
                String targetValue = switch (rule.getFieldToMatch().toLowerCase()) {
                    case "title" -> title;
                    case "body" -> body;
                    case "author" -> senderLogin;
                    default -> "";
                };

                boolean matches = evaluateMatch(targetValue, rule.getMatchOperator(), rule.getMatchValue());
                if (matches) {
                    matchedRuleName = rule.getName();
                    log.info("Rule '{}' matched for {}/{}#{}", rule.getName(), owner, repoName, itemNumber);

                    // Optional AI Triage Step
                    if (rule.isEnableAiTriage()) {
                        aiSummary = geminiAiService.summarizeAndTriage(title, body);
                    }

                    // Action 1: Add Label
                    if (rule.isAddLabelAction() && itemNumber > 0 && rule.getLabelToApply() != null) {
                        boolean ok = gitHubApiClient.addLabels(owner, repoName, itemNumber, List.of(rule.getLabelToApply()));
                        executedActionSummaries.add("GitHub: Applied label [" + rule.getLabelToApply() + "] (" + (ok ? "OK" : "Skipped/Failed") + ")");
                    }

                    // Action 2: Post Comment
                    if (rule.isPostCommentAction() && itemNumber > 0 && rule.getCommentBody() != null) {
                        String finalComment = rule.getCommentBody();
                        if (aiSummary != null && !aiSummary.isBlank()) {
                            finalComment += "\n\n?? *AI Triage:* " + aiSummary;
                        }
                        boolean ok = gitHubApiClient.postComment(owner, repoName, itemNumber, finalComment);
                        executedActionSummaries.add("GitHub: Posted comment (" + (ok ? "OK" : "Skipped/Failed") + ")");
                    }

                    // Action 3: Slack Notification
                    if (rule.isSendSlackAlert()) {
                        String details = String.join(", ", executedActionSummaries);
                        boolean ok = slackNotificationService.sendNotification(
                                title, eventType + " (" + action + ")", repoFullName, senderLogin, htmlUrl, details, aiSummary
                        );
                        executedActionSummaries.add("Slack: Alert dispatched (" + (ok ? "OK" : "Skipped/Failed") + ")");
                    }
                    break; // stop on first matching rule
                }
            }

            // 4. Record Audit Log
            AuditLog.ExecutionStatus finalStatus = matchedRuleName != null ? AuditLog.ExecutionStatus.SUCCESS : AuditLog.ExecutionStatus.NO_RULE_MATCHED;
            String actionsResult = executedActionSummaries.isEmpty() ? "No actions executed" : String.join(" | ", executedActionSummaries);

            recordAudit(deliveryGuid, eventType, action, repoFullName, matchedRuleName != null ? matchedRuleName : "None",
                    actionsResult, aiSummary, finalStatus, null, System.currentTimeMillis() - startTime);

            if (delivery != null) {
                delivery.setStatus(WebhookDelivery.DeliveryStatus.PROCESSED);
                deliveryRepository.save(delivery);
            }

        } catch (Exception e) {
            log.error("Error processing delivery {}: {}", deliveryGuid, e.getMessage(), e);
            recordAudit(deliveryGuid, eventType, null, "", "None", "Failed during execution",
                    null, AuditLog.ExecutionStatus.FAILED, e.getMessage(), System.currentTimeMillis() - startTime);

            if (delivery != null) {
                delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
                deliveryRepository.save(delivery);
            }
        }
    }

    private boolean evaluateMatch(String actual, AutomationRule.MatchOperator operator, String expected) {
        if (actual == null || expected == null) return false;
        String a = actual.toLowerCase();
        String e = expected.toLowerCase();
        return switch (operator) {
            case CONTAINS -> a.contains(e);
            case EQUALS -> a.equals(e);
            case STARTS_WITH -> a.startsWith(e);
            case REGEX -> actual.matches(expected);
        };
    }

    private void recordAudit(String deliveryGuid, String eventType, String action, String repo, String rule,
                            String actionsTaken, String aiSummary, AuditLog.ExecutionStatus status,
                            String error, long durationMs) {
        AuditLog logEntry = AuditLog.builder()
                .deliveryGuid(deliveryGuid)
                .eventType(eventType)
                .action(action)
                .repository(repo)
                .ruleTriggered(rule)
                .actionsTaken(actionsTaken)
                .aiSummary(aiSummary)
                .status(status)
                .errorMessage(error)
                .executionTimeMs(durationMs)
                .createdAt(LocalDateTime.now())
                .build();
        auditLogRepository.save(logEntry);
    }
}
