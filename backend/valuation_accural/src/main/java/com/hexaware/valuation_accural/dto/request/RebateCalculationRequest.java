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
public class RebateCalculationRequest {
  @NotBlank
  @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}")
  private String calculationRequestReference;

  @NotBlank
  @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}")
  private String transactionReference;

  @NotNull private LocalDate accrualDate;
  @NotNull private Instant cutoff;
  @NotNull @Positive private BigDecimal settledCashCollateralAmount;
  @NotNull @PositiveOrZero private BigDecimal annualRebatePercentage;
  @NotNull private LocalDate agreementEffectiveDate;
  private LocalDate agreementExpiryDate;
  private LocalDate contractCloseDate;
  @NotBlank @Pattern(regexp = "[A-Z]{3}") private String currency;
  @NotBlank private String dayCountBasis;
  @NotBlank private String inputVersion;
  @NotBlank private String borrowerOrganizationReference;
  @NotBlank private String custodianOrganizationReference;
  private String supersedesResultReference;
}
