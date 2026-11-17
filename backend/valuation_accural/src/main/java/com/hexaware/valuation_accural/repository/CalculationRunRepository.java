package com.hexaware.valuation_accural.repository;

import com.hexaware.valuation_accural.entity.CalculationRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalculationRunRepository extends JpaRepository<CalculationRun, Long> {
  Optional<CalculationRun> findByRequesterServiceAndRequestReference(
      String requesterService, String requestReference);
}
