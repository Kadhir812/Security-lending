package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.Organization;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationRepository
    extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

  Optional<Organization> findByOrganizationReference(String reference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select organization from Organization organization where organization.organizationReference = :reference")
  Optional<Organization> findLocked(String reference);

  @Query(
      """
      select organization
      from Organization organization
      where organization.organizationId = :custodian
        or organization.registeredByCustodianId = :custodian
        or exists (
          select mandate.mandateId
          from LenderMandate mandate
          where mandate.lenderOrgId = organization.organizationId
            and mandate.custodianOrgId = :custodian
        )
        or exists (
          select agreement.mslaId
          from MslaAgreement agreement, LenderMandate mandate
          where agreement.mandateId = mandate.mandateId
            and agreement.borrowerOrgId = organization.organizationId
            and mandate.custodianOrgId = :custodian
        )
      """)
  Page<Organization> findVisible(Long custodian, Pageable pageable);
}
