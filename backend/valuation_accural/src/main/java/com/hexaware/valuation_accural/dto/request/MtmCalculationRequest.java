package com.hexaware.valuation_accural.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MtmCalculationRequest {
  @NotBlank @Pattern(regexp = "[A-Z]{2}[A-Z0-9]{9}[0-9]") private String securityIsin;
  @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}") private String calculationRequestReference;
  @NotNull @PositiveOrZero private Long outstandingQuantity;
  @NotNull @Positive private BigDecimal coveragePercentage;
  @NotNull private BigDecimal confirmedCashCollateral;
  @NotBlank @Pattern(regexp = "[A-Z]{3}") private String currency;
  @NotBlank private String custodianOrganizationReference;
  @NotBlank private String borrowerOrganizationReference;
  @NotBlank private String transactionReference;
  @NotNull private Instant inputAsOf;
  @NotNull private Long inputVersion;
}
