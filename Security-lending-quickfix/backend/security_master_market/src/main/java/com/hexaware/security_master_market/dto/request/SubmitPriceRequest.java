package com.hexaware.security_master_market.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class SubmitPriceRequest {
  @NotNull
  @DecimalMin(value = "0", inclusive = false)
  @Digits(integer = 14, fraction = 6)
  private BigDecimal marketPrice;

  @NotNull private Instant priceTimestamp;

  @NotBlank
  @Size(max = 100)
  private String sourceName;
}
