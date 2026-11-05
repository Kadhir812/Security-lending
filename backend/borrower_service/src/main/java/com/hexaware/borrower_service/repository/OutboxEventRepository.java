package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {}
