package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_deliveries", indexes = {
    @Index(name = "idx_delivery_guid", columnList = "deliveryGuid", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String deliveryGuid;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(length = 64)
    private String action;

    @Column(length = 128)
    private String repository;

    @Column(length = 64)
    private String senderLogin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    public enum DeliveryStatus {
        RECEIVED,
        PROCESSING,
        PROCESSED,
        FAILED,
        DUPLICATE_IGNORED
    }
}
