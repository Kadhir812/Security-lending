package com.hexaware.security_master_market.dto.request;

import com.hexaware.security_master_market.enums.SecurityStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSecurityStatusRequest {
  @NotNull @PositiveOrZero private Long version;

  @NotNull private SecurityStatus status;
}
