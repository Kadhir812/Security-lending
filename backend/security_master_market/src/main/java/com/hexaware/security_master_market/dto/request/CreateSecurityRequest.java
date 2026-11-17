package com.hexaware.security_master_market.dto.request;

import com.hexaware.security_master_market.enums.SecurityStatus;
import com.hexaware.security_master_market.enums.SecurityType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
  @JsonAlias("name")
  private String securityName;

  @JsonAlias("type")
  @NotNull private SecurityType securityType;

  @NotBlank
  @Size(max = 3)
  private String currency;

  @Size(max = 100)
  @JsonAlias("exchange")
  private String exchangeName;

  @Size(max = 7)
  private String sedol;

  @Size(max = 100)
  private String sector;

  @Builder.Default
  @NotNull
  @Positive
  private Long lotSize = 1L;

  @NotBlank private String isin;

  @Builder.Default @NotNull private SecurityStatus status = SecurityStatus.ACTIVE;
}
