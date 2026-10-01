package com.example.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GitHubApiClient {

    @Value("${app.github.bot-token:}")
    private String botToken;

    @Value("${app.github.api-url:https://api.github.com}")
    private String apiUrl;

    private RestClient getClient() {
        return RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public boolean addLabels(String owner, String repo, int issueNumber, List<String> labels) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("GITHUB_BOT_TOKEN is not configured; skipping write-back to GitHub");
            return false;
        }

        try {
            getClient().post()
                    .uri("/repos/{owner}/{repo}/issues/{issueNumber}/labels", owner, repo, issueNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("labels", labels))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully added labels {} to {}/{}#{}", labels, owner, repo, issueNumber);
            return true;
        } catch (Exception e) {
            log.error("Failed to add labels to {}/{}#{}: {}", owner, repo, issueNumber, e.getMessage());
            return false;
        }
    }

    public boolean postComment(String owner, String repo, int issueNumber, String commentBody) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("GITHUB_BOT_TOKEN is not configured; skipping comment post");
            return false;
        }

        try {
            getClient().post()
                    .uri("/repos/{owner}/{repo}/issues/{issueNumber}/comments", owner, repo, issueNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("body", commentBody))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully posted comment to {}/{}#{}", owner, repo, issueNumber);
            return true;
        } catch (Exception e) {
            log.error("Failed to post comment to {}/{}#{}: {}", owner, repo, issueNumber, e.getMessage());
            return false;
        }
    }
}
