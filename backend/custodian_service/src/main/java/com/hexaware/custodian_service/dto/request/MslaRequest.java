package com.hexaware.custodian_service.dto.request;

import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.CollateralType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MslaRequest {
  @NotBlank
  @Size(max = 80)
  private String mslaReference;

  @NotBlank
  @Size(max = 80)
  private String mandateReference;

  @NotBlank
  @Size(max = 80)
  private String borrowerOrganizationReference;

  @NotNull
  private LocalDate effectiveDate;

  private LocalDate expiryDate;

  @NotNull
  private AgreementStatus agreementStatus;

  @NotBlank
  private String baseCurrency;

  @NotNull
  @Valid
  private EligibleSecurities eligibleSecurities;

  @NotNull
  private CollateralType collateralType;

  @NotNull
  @DecimalMin("100")
  @Digits(integer = 5, fraction = 4)
  private BigDecimal collateralCoveragePct;

  @NotNull
  @Positive
  private Integer mtmIntervalMinutes;

  @NotNull
  @Positive
  private Integer marginCallWindowHours;

  @Size(max = 500)
  private String recallRule;

  private Map<String, Object> defaultRules;

  @NotBlank
  @Size(max = 80)
  private String settlementCalendarCode;

  @PositiveOrZero
  private Long version;
}
