package com.hexaware.borrower_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeePaymentRequest {
  @NotNull
  @DecimalMin("0.01")
  @Digits(integer = 18, fraction = 2)
  private BigDecimal amount;

  @NotNull @Pattern(regexp = "[A-Z]{3}") private String currency;

  @NotEmpty @Size(max = 100)
  private List<@Valid FeePaymentAllocationRequest> allocations;
}
