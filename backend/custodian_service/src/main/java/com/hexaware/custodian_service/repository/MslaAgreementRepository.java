package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.MslaAgreement;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MslaAgreementRepository
    extends JpaRepository<MslaAgreement, Long>, JpaSpecificationExecutor<MslaAgreement> {

  Optional<MslaAgreement> findByMslaReference(String reference);

  @Query(
      """
      select agreement
      from MslaAgreement agreement, LenderMandate mandate
      where agreement.mandateId = mandate.mandateId
        and agreement.mslaReference = :reference
        and mandate.custodianOrgId = :custodian
      """)
  Optional<MslaAgreement> findScoped(String reference, Long custodian);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select agreement
      from MslaAgreement agreement, LenderMandate mandate
      where agreement.mandateId = mandate.mandateId
        and agreement.mslaReference = :reference
        and mandate.custodianOrgId = :custodian
      """)
  Optional<MslaAgreement> findScopedLocked(String reference, Long custodian);

  @Query(
      """
      select distinct agreement
      from MslaAgreement agreement, LenderMandate mandate,
          Organization borrower, Organization lender, Organization custodian
      where agreement.mandateId = mandate.mandateId
        and agreement.borrowerOrgId = borrower.organizationId
        and mandate.lenderOrgId = lender.organizationId
        and mandate.custodianOrgId = custodian.organizationId
        and borrower.organizationId = :borrowerId
        and agreement.agreementStatus =
            com.hexaware.custodian_service.enums.AgreementStatus.ACTIVE
        and agreement.effectiveDate <= :today
        and (agreement.expiryDate is null or agreement.expiryDate >= :today)
        and mandate.status = com.hexaware.custodian_service.enums.MandateStatus.ACTIVE
        and mandate.effectiveFrom <= :today
        and (mandate.effectiveTo is null or mandate.effectiveTo >= :today)
        and borrower.organizationType =
            com.hexaware.custodian_service.enums.OrganizationType.BORROWER
        and borrower.status =
            com.hexaware.custodian_service.enums.OrganizationStatus.ACTIVE
        and lender.organizationType =
            com.hexaware.custodian_service.enums.OrganizationType.LENDER
        and lender.status = com.hexaware.custodian_service.enums.OrganizationStatus.ACTIVE
        and custodian.organizationType =
            com.hexaware.custodian_service.enums.OrganizationType.CUSTODIAN
        and custodian.status = com.hexaware.custodian_service.enums.OrganizationStatus.ACTIVE
      """)
  List<MslaAgreement> findEligibleForBorrower(Long borrowerId, LocalDate today);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select agreement from MslaAgreement agreement where agreement.mslaId = :id")
  Optional<MslaAgreement> findLocked(Long id);
}
