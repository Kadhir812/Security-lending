package com.hexaware.valuation_accural.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LendingFeeCalculationRequest {
  @NotBlank
  @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}")
  private String calculationRequestReference;

  @NotBlank
  @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}")
  private String transactionReference;

  @NotBlank
  @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{9}[0-9]")
  private String securityIsin;

  @NotNull private LocalDate accrualDate;
  @NotNull private Instant cutoff;
  @NotNull @Positive private Long settledOutstandingQuantity;
  @NotNull @PositiveOrZero private BigDecimal annualFeePercentage;
  @NotBlank @Pattern(regexp = "[A-Z]{3}") private String currency;
  @NotBlank private String dayCountBasis;
  @NotBlank private String inputVersion;
  @NotBlank private String borrowerOrganizationReference;
  @NotBlank private String custodianOrganizationReference;
  private String supersedesResultReference;
}
