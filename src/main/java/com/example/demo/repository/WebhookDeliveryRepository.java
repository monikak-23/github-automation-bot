package com.example.demo.repository;

import com.example.demo.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    Optional<WebhookDelivery> findByDeliveryGuid(String deliveryGuid);
    boolean existsByDeliveryGuid(String deliveryGuid);
}
