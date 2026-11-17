package com.hexaware.valuation_accural.repository;

import com.hexaware.valuation_accural.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {}
