package com.example.demo.controller;

import com.example.demo.entity.AuditLog;
import com.example.demo.entity.AutomationRule;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.repository.AutomationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final AuditLogRepository auditLogRepository;
    private final AutomationRuleRepository ruleRepository;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal != null) {
            return "redirect:/dashboard";
        }
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OAuth2User principal, Model model) {
        String username = principal != null ? principal.getAttribute("login") : "Guest";
        String avatarUrl = principal != null ? principal.getAttribute("avatar_url") : null;

        List<AuditLog> auditLogs = auditLogRepository.findTop50ByOrderByCreatedAtDesc();
        List<AutomationRule> rules = ruleRepository.findAll();

        model.addAttribute("username", username);
        model.addAttribute("avatarUrl", avatarUrl);
        model.addAttribute("auditLogs", auditLogs);
        model.addAttribute("rules", rules);
        return "dashboard";
    }

    @PostMapping("/api/rules")
    public String createRule(
            @RequestParam String name,
            @RequestParam String eventType,
            @RequestParam(defaultValue = "ANY") String actionFilter,
            @RequestParam String fieldToMatch,
            @RequestParam AutomationRule.MatchOperator matchOperator,
            @RequestParam String matchValue,
            @RequestParam(defaultValue = "false") boolean addLabelAction,
            @RequestParam(required = false) String labelToApply,
            @RequestParam(defaultValue = "false") boolean postCommentAction,
            @RequestParam(required = false) String commentBody,
            @RequestParam(defaultValue = "false") boolean sendSlackAlert,
            @RequestParam(defaultValue = "false") boolean enableAiTriage
    ) {
        AutomationRule rule = AutomationRule.builder()
                .name(name)
                .eventType(eventType)
                .actionFilter(actionFilter)
                .fieldToMatch(fieldToMatch)
                .matchOperator(matchOperator)
                .matchValue(matchValue)
                .addLabelAction(addLabelAction)
                .labelToApply(labelToApply)
                .postCommentAction(postCommentAction)
                .commentBody(commentBody)
                .sendSlackAlert(sendSlackAlert)
                .enableAiTriage(enableAiTriage)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        ruleRepository.save(rule);
        return "redirect:/dashboard";
    }

    @PostMapping("/api/rules/{id}/toggle")
    public String toggleRule(@PathVariable Long id) {
        ruleRepository.findById(id).ifPresent(r -> {
            r.setActive(!r.isActive());
            ruleRepository.save(r);
        });
        return "redirect:/dashboard";
    }

    @PostMapping("/api/rules/{id}/delete")
    public String deleteRule(@PathVariable Long id) {
        ruleRepository.deleteById(id);
        return "redirect:/dashboard";
    }
}
