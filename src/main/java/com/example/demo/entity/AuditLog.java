package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_audit_logs", indexes = {
    @Index(name = "idx_audit_delivery", columnList = "deliveryGuid"),
    @Index(name = "idx_audit_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String deliveryGuid;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(length = 64)
    private String action;

    @Column(length = 128)
    private String repository;

    @Column(length = 255)
    private String ruleTriggered;

    @Column(length = 500)
    private String actionsTaken;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExecutionStatus status;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Long executionTimeMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        NO_RULE_MATCHED,
        BOT_LOOP_PREVENTED
    }
}
