package com.example.demo.repository;

import com.example.demo.entity.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {
    List<AutomationRule> findByActiveTrue();
    List<AutomationRule> findByEventTypeAndActiveTrue(String eventType);
}
