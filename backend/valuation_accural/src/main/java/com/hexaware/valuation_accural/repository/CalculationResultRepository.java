package com.hexaware.valuation_accural.repository;

import com.hexaware.valuation_accural.entity.CalculationResult;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalculationResultRepository extends JpaRepository<CalculationResult, Long> {
  Optional<CalculationResult> findByRunIdAndItemKey(Long runId, String itemKey);
  Optional<CalculationResult> findByResultReference(String resultReference);
  List<CalculationResult>
      findByTransactionReferenceAndResultTypeAndCalculationDateBetweenOrderByCalculationDateAscCreatedAtAsc(
          String transactionReference, String resultType, LocalDate from, LocalDate to);
}
