package com.hexaware.custodian_service.dto.response;

import com.hexaware.custodian_service.enums.LoanRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustodianLoanRequestResponse {
  private String requestReference;
  private String mslaReference;
  private String borrowerOrganizationReference;
  private String lenderOrganizationReference;
  private String custodianOrganizationReference;
  private String securityIsin;
  private Long quantity;
  private LocalDate requestedSettlementDate;
  private String settlementCurrency;
  private String submittedByIssuer;
  private String submittedBySubject;
  private Instant requestedAt;
  private LoanRequestStatus status;
}
