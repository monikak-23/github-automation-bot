package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiAiService {

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.gemini.api-url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String geminiApiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String summarizeAndTriage(String title, String body) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return "AI triage skipped: GEMINI_API_KEY is not configured.";
        }

        try {
            String prompt = String.format(
                    "You are an automated triage bot for GitHub issues and PRs. Analyze this item:\n" +
                    "Title: %s\n" +
                    "Body: %s\n\n" +
                    "Respond with a brief 1-2 sentence summary, suggested priority (P0/P1/P2/P3), and suggested category (Bug/Feature/Chore/Docs).",
                    title, body != null ? body : "None"
            );

            Map<String, Object> requestPayload = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );

            String url = geminiApiUrl + "?key=" + geminiApiKey;

            String response = RestClient.create().post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!textNode.isMissingNode()) {
                return textNode.asText().trim();
            }
        } catch (Exception e) {
            log.error("Gemini AI API call failed: {}", e.getMessage());
            return "AI triage failed: " + e.getMessage();
        }
        return "No summary generated.";
    }
}
