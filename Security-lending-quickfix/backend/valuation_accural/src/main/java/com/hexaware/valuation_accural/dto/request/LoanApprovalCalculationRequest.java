package com.hexaware.valuation_accural.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApprovalCalculationRequest {
  @NotBlank
  @Pattern(regexp = "[A-Z0-9]{12}")
  private String securityIsin;

  @NotNull @Positive private Long quantity;
  @NotNull @Positive private BigDecimal coveragePercentage;

  @NotBlank
  @Pattern(regexp = "[A-Z]{3}")
  private String currency;

  @NotBlank
  @Pattern(regexp = "[A-Z0-9_-]{1,80}")
  private String custodianOrganizationReference;
}
