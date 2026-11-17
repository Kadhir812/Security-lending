package com.hexaware.borrower_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeePaymentAllocationRequest {
  @NotBlank
  @Pattern(regexp = "[0-9a-fA-F-]{36}")
  private String calculationResultReference;

  @NotBlank
  @Pattern(regexp = "LENDING_FEE:[0-9]{4}-[0-9]{2}-[0-9]{2}")
  private String itemKey;

  @NotNull
  @DecimalMin("0.01")
  @Digits(integer = 18, fraction = 2)
  private BigDecimal amount;
}
