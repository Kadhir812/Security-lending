package com.hexaware.valuation_accural.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
  @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}")
  private String calculationRequestReference;

  @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}")
  private String transactionReference;

  @NotNull private LocalDate accrualDate;
  @NotNull private Instant cutoff;
  @NotNull @PositiveOrZero @Digits(integer = 18, fraction = 2)
  private BigDecimal settledCashCollateral;
  @NotNull @PositiveOrZero @Digits(integer = 4, fraction = 6)
  private BigDecimal annualRebatePercentage;
  @NotBlank @Pattern(regexp = "[A-Z]{3}")
  private String currency;
  @NotBlank private String dayCountBasis;
  @NotBlank @Pattern(regexp = "[A-Fa-f0-9]{64}")
  private String inputVersion;
  @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}")
  private String borrowerOrganizationReference;
  @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}")
  private String custodianOrganizationReference;
  private String supersedesResultReference;
}
