package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SlackNotificationService {

    @Value("${app.slack.webhook-url:}")
    private String slackWebhookUrl;

    public boolean sendNotification(String title, String eventType, String repo, String author, String url, String details, String aiSummary) {
        if (slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
            log.warn("SLACK_WEBHOOK_URL is not configured; skipping Slack notification");
            return false;
        }

        try {
            List<Map<String, Object>> blocks = new ArrayList<>();

            // Header Block
            blocks.add(Map.of(
                    "type", "header",
                    "text", Map.of("type", "plain_text", "text", "?? GitHub Automation Event: " + eventType, "emoji", true)
            ));

            // Section Block with metadata
            String metaText = String.format("*Repository:* <%s|%s>\n*Author:* %s\n*Subject:* <%s|%s>",
                    "https://github.com/" + repo, repo, author, url, title);

            blocks.add(Map.of(
                    "type", "section",
                    "text", Map.of("type", "mrkdwn", "text", metaText)
            ));

            // Optional AI Summary Block
            if (aiSummary != null && !aiSummary.isBlank()) {
                blocks.add(Map.of(
                        "type", "section",
                        "text", Map.of("type", "mrkdwn", "text", "?? *AI Insights & Triage:*\n> " + aiSummary)
                ));
            }

            // Details Block
            if (details != null && !details.isBlank()) {
                blocks.add(Map.of(
                        "type", "context",
                        "elements", List.of(Map.of("type", "mrkdwn", "text", "? Action taken: " + details))
                ));
            }

            Map<String, Object> payload = Map.of("blocks", blocks);

            RestClient.create().post()
                    .uri(slackWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Sent Slack notification for {} in {}", eventType, repo);
            return true;
        } catch (Exception e) {
            log.error("Failed to send Slack alert: {}", e.getMessage());
            return false;
        }
    }
}
