package com.hexaware.borrower_service.service.loan;

import com.hexaware.borrower_service.client.CustodianLocateClient;
import com.hexaware.borrower_service.client.CustodianMslaClient;
import com.hexaware.borrower_service.dto.request.LocateSubmission;
import com.hexaware.borrower_service.dto.response.LocateResponse;
import com.hexaware.borrower_service.entity.LocateRequest;
import com.hexaware.borrower_service.enums.LocateStatus;
import com.hexaware.borrower_service.enums.SubmissionStatus;
import com.hexaware.borrower_service.repository.LocateRequestRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BorrowerLocateService {
  private final LoanRequestSubmissionValidator access;
  private final CustodianLocateClient custodian;
  private final CustodianMslaClient mslas;
  private final LocateRequestRepository locates;

  @Transactional
  public LocateResponse submit(Authentication authentication, LocateSubmission submission) {
    var borrower = access.requireBorrower(authentication);
    var msla = mslas.require(authentication, submission.mslaReference());
    if (!borrower.getOrganizationReference().equals(msla.borrowerOrganizationReference()))
      throw com.hexaware.borrower_service.exception.BorrowerException.conflict(
          "Selected MSLA is not owned by this borrower");
    var remote = custodian.submit(authentication, submission);
    var entity = LocateRequest.builder()
        .borrowerOrganizationReference(borrower.getOrganizationReference())
        .custodianOrganizationReference(msla.custodianOrganizationReference())
        .submissionStatus(SubmissionStatus.SENT.name())
        .decisionSourceVersion(0L)
        .version(0L)
        .locateReference(remote.locateReference())
        .mslaReference(remote.mslaReference())
        .securityIsin(remote.securityIsin())
        .quantity(remote.quantity())
        .createdBy(borrower.getUserId())
        .requestTime(remote.requestTime())
        .expiryTime(remote.expiryTime())
        .status(LocateStatus.valueOf(remote.status()))
        .build();
    locates.save(entity);
    return remote;
  }

  @Transactional(readOnly = true)
  public List<LocateResponse> list(Authentication authentication) {
    var borrower = access.requireBorrower(authentication);
    return locates.findByBorrowerOrganizationReferenceOrderByRequestTimeDesc(
        borrower.getOrganizationReference()).stream()
        .map(row -> custodian.get(authentication, row.getLocateReference()))
        .toList();
  }

  public LocateResponse get(Authentication authentication, String reference) {
    var borrower = access.requireBorrower(authentication);
    locates.findByLocateReferenceAndBorrowerOrganizationReference(
        reference, borrower.getOrganizationReference()).orElseThrow(
            () -> com.hexaware.borrower_service.exception.BorrowerException
                .missing("Locate not found"));
    return custodian.get(authentication, reference);
  }
}
