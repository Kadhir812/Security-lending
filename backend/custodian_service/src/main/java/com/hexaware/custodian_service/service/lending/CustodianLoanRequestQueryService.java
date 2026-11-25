package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.dto.response.CustodianLoanRequestResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustodianLoanRequestQueryService {
  private final SetupAccess access;
  private final LoanRequestRepository requests;

  @Transactional(readOnly = true)
  public PageResponse<CustodianLoanRequestResponse> list(
      Authentication authentication, int page, int size) {
    var custodian = access.actor(authentication, false);
    if (page < 0 || size < 1 || size > 100) {
      throw SetupException.invalid("Invalid page or size");
    }
    return PageResponse.from(
        requests.findReceivedByCustodian(custodian.getOrganizationId(), PageRequest.of(page, size)));
  }

  @Transactional(readOnly = true)
  public CustodianLoanRequestResponse get(Authentication authentication, String requestReference) {
    var custodian = access.actor(authentication, false);
    return requests
        .findReceivedByCustodianAndReference(custodian.getOrganizationId(), requestReference)
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
  }
}
