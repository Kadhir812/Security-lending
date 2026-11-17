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
  private Long id;
  private Long securityId;
  private Long version;
  private String isin;
  private String symbol;
  private String securityName;
  private String name;
  private SecurityType securityType;
  private SecurityType type;
  private String currency;
  private String exchangeName;
  private String exchange;
  private String sedol;
  private String sector;
  private Long lotSize;
  private SecurityStatus status;
}
