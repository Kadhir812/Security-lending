package com.hexaware.security_master_market.dto.request;

import com.hexaware.security_master_market.enums.SecurityStatus;
import com.hexaware.security_master_market.enums.SecurityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSecurityRequest {
  @NotBlank
  @Size(max = 50)
  private String symbol;

  @NotBlank
  @Size(max = 255)
  private String securityName;

  @NotNull private SecurityType securityType;

  @NotBlank
  @Size(max = 3)
  private String currency;

  @Size(max = 100)
  private String exchangeName;

  @NotBlank private String isin;

  @Builder.Default @NotNull private SecurityStatus status = SecurityStatus.ACTIVE;
}
