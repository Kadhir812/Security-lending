package com.hexaware.borrower_service.service.loan;

import com.hexaware.borrower_service.dto.request.LoanRequestSubmission;
import com.hexaware.borrower_service.dto.response.BorrowerEligibleMslaResponse;
import com.hexaware.borrower_service.dto.response.LoanContractViewResponse;
import com.hexaware.borrower_service.dto.response.LoanRequestResponse;
import com.hexaware.borrower_service.dto.response.PageResponse;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;

@Service
@RequiredArgsConstructor
public class LoanRequestService {
  private final LoanRequestRepository requests;
  private final LoanRequestWriter writer;
  private final LoanRequestSubmissionValidator validator;
  private final LoanRequestResponseMapper responseMapper;
  private final LoanRequestQueryService queries;

  public List<BorrowerEligibleMslaResponse> eligibleMslaList(Authentication authentication) {
    return validator.eligibleMslaList(authentication);
  }

  public LoanRequestResponse submit(
      Authentication authentication,
      String idempotencyKey,
      LoanRequestSubmission submission) {
    var borrower = validator.requireBorrower(authentication);
    validator.validateIdempotencyKey(idempotencyKey);

    LoanRequest previous = requests.findByRequestReference(idempotencyKey).orElse(null);
    if (previous != null) {
      if (!previous
              .getBorrowerOrganizationReference()
              .equals(borrower.getOrganizationReference())
          || !responseMapper.matches(previous, submission)) {
        throw BorrowerException.conflict("Idempotency key was already used for another request");
      }
      return responseMapper.toResponse(previous);
    }

    LoanRequestSubmissionValidator.ValidatedSubmission validated =
        validator.validate(authentication, submission);
    LoanRequest saved =
        writer.submit(
            validated.borrower().getUserId(),
            validated.borrower().getOrganizationReference(),
            validated.borrower().getToken().getClaimAsString("iss"),
            validated.borrower().getToken().getSubject(),
            idempotencyKey,
            submission,
            validated.custodianOrganizationReference(),
            validated.locateId());
    return responseMapper.toResponse(saved);
  }

  public PageResponse<LoanRequestResponse> list(
      Authentication authentication, int page, int size) {
    return queries.list(authentication, page, size);
  }

  public LoanRequestResponse get(Authentication authentication, String reference) {
    return queries.get(authentication, reference);
  }

  public LoanContractViewResponse contract(
      Authentication authentication, String requestReference) {
    return queries.contract(authentication, requestReference);
  }
}
