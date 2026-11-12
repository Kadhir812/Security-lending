package com.hexaware.borrower_service.service.loan;

import com.hexaware.borrower_service.dto.response.LoanContractViewResponse;
import com.hexaware.borrower_service.dto.response.LoanRequestResponse;
import com.hexaware.borrower_service.dto.response.PageResponse;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanRequestQueryService {
  private final LoanRequestRepository requests;
  private final LoanContractViewRepository contractViews;
  private final LoanRequestSubmissionValidator validator;
  private final LoanRequestResponseMapper responseMapper;

  public PageResponse<LoanRequestResponse> list(
      Authentication authentication, int page, int size) {
    var borrower = validator.requireBorrower(authentication);
    if (page < 0 || size < 1 || size > 100) {
      throw BorrowerException.invalid("Invalid page or size");
    }
    return PageResponse.from(
        requests
            .findByBorrowerOrganizationReferenceOrderByRequestedAtDesc(
                borrower.getOrganizationReference(), PageRequest.of(page, size))
            .map(responseMapper::toResponse));
  }

  public LoanRequestResponse get(Authentication authentication, String reference) {
    var borrower = validator.requireBorrower(authentication);
    return requests
        .findByRequestReferenceAndBorrowerOrganizationReference(
            reference, borrower.getOrganizationReference())
        .map(responseMapper::toResponse)
        .orElseThrow(() -> BorrowerException.missing("Loan request not found"));
  }

  public LoanContractViewResponse contract(
      Authentication authentication, String requestReference) {
    var borrower = validator.requireBorrower(authentication);
    requests
        .findByRequestReferenceAndBorrowerOrganizationReference(
            requestReference, borrower.getOrganizationReference())
        .orElseThrow(() -> BorrowerException.missing("Loan request not found"));
    var view =
        contractViews
            .findByRequestReference(requestReference)
            .orElseThrow(() -> BorrowerException.missing("Approved contract view not found"));
    return new LoanContractViewResponse(
        view.getRequestReference(),
        view.getTransactionReference(),
        view.getContractSnapshot(),
        view.getReceivedAt());
  }
}
