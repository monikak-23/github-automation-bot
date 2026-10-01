package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "automation_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutomationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 64)
    private String eventType; // "issues", "pull_request"

    @Column(length = 64)
    private String actionFilter; // "opened", "closed", "labeled" or "ANY"

    @Column(nullable = false, length = 32)
    private String fieldToMatch; // "title", "body", "author"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchOperator matchOperator; // CONTAINS, EQUALS, REGEX

    @Column(nullable = false, length = 255)
    private String matchValue;

    @Column(nullable = false)
    private boolean addLabelAction;

    @Column(length = 100)
    private String labelToApply;

    @Column(nullable = false)
    private boolean postCommentAction;

    @Column(length = 500)
    private String commentBody;

    @Column(nullable = false)
    private boolean sendSlackAlert;

    @Column(nullable = false)
    private boolean enableAiTriage;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum MatchOperator {
        CONTAINS,
        EQUALS,
        STARTS_WITH,
        REGEX
    }
}
