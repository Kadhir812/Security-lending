package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.dto.response.CustodianLoanRequestResponse;
import com.hexaware.custodian_service.entity.LoanRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {
  Optional<LoanRequest> findByRequestReference(String requestReference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select request from LoanRequest request where request.requestReference = :reference")
  Optional<LoanRequest> findLockedByRequestReference(String reference);

  @Query(
      """
      select new com.hexaware.custodian_service.dto.response.CustodianLoanRequestResponse(
          request.requestReference, agreement.mslaReference, borrower.organizationReference,
          lender.organizationReference, custodian.organizationReference, request.securityIsin,
          request.quantity, request.requestedSettlementDate, request.settlementCurrency,
          request.submittedByIssuer, request.submittedBySubject, request.requestedAt, request.status)
      from LoanRequest request, MslaAgreement agreement, LenderMandate mandate,
          Organization borrower, Organization lender, Organization custodian
      where request.mslaId = agreement.mslaId
        and agreement.mandateId = mandate.mandateId
        and agreement.borrowerOrgId = borrower.organizationId
        and mandate.lenderOrgId = lender.organizationId
        and mandate.custodianOrgId = custodian.organizationId
        and mandate.custodianOrgId = :custodianId
      order by request.requestedAt desc, request.requestId desc
      """)
  Page<CustodianLoanRequestResponse> findReceivedByCustodian(
      Long custodianId, Pageable pageable);

  @Query(
      """
      select new com.hexaware.custodian_service.dto.response.CustodianLoanRequestResponse(
          request.requestReference, agreement.mslaReference, borrower.organizationReference,
          lender.organizationReference, custodian.organizationReference, request.securityIsin,
          request.quantity, request.requestedSettlementDate, request.settlementCurrency,
          request.submittedByIssuer, request.submittedBySubject, request.requestedAt, request.status)
      from LoanRequest request, MslaAgreement agreement, LenderMandate mandate,
          Organization borrower, Organization lender, Organization custodian
      where request.mslaId = agreement.mslaId
        and agreement.mandateId = mandate.mandateId
        and agreement.borrowerOrgId = borrower.organizationId
        and mandate.lenderOrgId = lender.organizationId
        and mandate.custodianOrgId = custodian.organizationId
        and mandate.custodianOrgId = :custodianId
        and request.requestReference = :requestReference
      """)
  Optional<CustodianLoanRequestResponse> findReceivedByCustodianAndReference(
      Long custodianId, String requestReference);
}
