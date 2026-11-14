package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.MarginCallTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarginCallTransactionLogRepository
    extends JpaRepository<MarginCallTransactionLog, Long> {}
