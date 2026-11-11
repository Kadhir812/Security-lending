package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.CreditLimitHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLimitHistoryRepository extends JpaRepository<CreditLimitHistory, Long> {
  List<CreditLimitHistory> findByCreditLimitIdOrderByChangedAtDesc(Long creditLimitId);
}
