package com.hexaware.security_master_market.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeedSecurity {
  @Valid @NotNull private CreateSecurityRequest security;
  @Valid @NotNull private SubmitPriceRequest price;
  @NotBlank private String priceReference;
}
