package com.hexaware.security_master_market.dto.response;

import com.hexaware.security_master_market.enums.SecurityStatus;
import com.hexaware.security_master_market.enums.SecurityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityResponse {
  private Long securityId;
  private Long version;
  private String isin;
  private String symbol;
  private String securityName;
  private SecurityType securityType;
  private String currency;
  private String exchangeName;
  private SecurityStatus status;
}
