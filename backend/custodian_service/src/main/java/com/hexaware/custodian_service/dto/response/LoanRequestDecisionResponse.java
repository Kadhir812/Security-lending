package com.hexaware.custodian_service.dto.response;

import com.hexaware.custodian_service.enums.LoanRequestStatus;
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
public class LoanRequestDecisionResponse {
  private String requestReference;
  private LoanRequestStatus status;
  private String reason;
  private String contractReference;
  private BigDecimal loanMarketValue;
  private BigDecimal requiredCashCollateral;
  private Instant respondedAt;
}
