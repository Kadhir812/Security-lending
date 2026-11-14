package com.hexaware.valuation_accural;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexaware.valuation_accural.dto.request.LoanApprovalCalculationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoanApprovalCalculationRequestTest {
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsLowercaseCustodianOrganizationReferences() {
    var request =
        LoanApprovalCalculationRequest.builder()
            .securityIsin("INE144J01027")
            .quantity(800L)
            .coveragePercentage(new BigDecimal("105.0000"))
            .currency("INR")
            .custodianOrganizationReference("CUSTODIAN-10597be65b714faf9aca3de56732981b")
            .build();

    assertTrue(validator.validate(request).isEmpty());
  }
}
