package com.hexaware.borrower_service.service.loan;

import com.hexaware.borrower_service.dto.request.LoanRequestSubmission;
import com.hexaware.borrower_service.dto.response.LoanRequestResponse;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.entity.LocateRequest;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoanRequestResponseMapper {
  private final LocateRequestRepository locates;

  public boolean matches(LoanRequest existing, LoanRequestSubmission submission) {
    String existingLocate =
        existing.getLocateId() == null
            ? null
            : locates
                .findById(existing.getLocateId())
                .map(LocateRequest::getLocateReference)
                .orElse(null);
    return existing.getMslaReference().equals(submission.mslaReference())
        && Objects.equals(existingLocate, submission.locateReference())
        && existing.getSecurityIsin().equals(submission.securityIsin())
        && existing.getQuantity().equals(submission.quantity())
        && existing.getRequestedSettlementDate().equals(submission.requestedSettlementDate())
        && Objects.equals(existing.getSettlementCurrency(), submission.settlementCurrency());
  }

  public LoanRequestResponse toResponse(LoanRequest request) {
    String locateReference =
        request.getLocateId() == null
            ? null
            : locates
                .findById(request.getLocateId())
                .map(LocateRequest::getLocateReference)
                .orElse(null);
    return new LoanRequestResponse(
        request.getRequestReference(),
        request.getMslaReference(),
        locateReference,
        request.getSecurityIsin(),
        request.getQuantity(),
        request.getRequestedSettlementDate(),
        request.getSettlementCurrency(),
        request.getSubmissionStatus(),
        request.getStatus(),
        request.getResponseReason(),
        request.getRespondedAt(),
        request.getRequestedAt(),
        request.getVersion());
  }
}
