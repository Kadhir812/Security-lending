package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LenderInventory;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LenderInventoryRepository extends JpaRepository<LenderInventory, Long> {

  Optional<LenderInventory> findByOrganizationIdAndSecurityIsin(
      Long organizationId, String securityIsin);

  boolean existsByOrganizationIdAndSecurityIsin(Long organizationId, String securityIsin);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select inventory
      from LenderInventory inventory
      where inventory.organizationId = :organizationId
        and inventory.securityIsin = :securityIsin
      """)
  Optional<LenderInventory> findLocked(Long organizationId, String securityIsin);

  @Query(
      value = """
      select distinct inventory
      from LenderInventory inventory, Organization lender, LenderMandate mandate
      where inventory.organizationId = lender.organizationId
        and mandate.lenderOrgId = lender.organizationId
        and mandate.custodianOrgId = :custodianId
        and mandate.status = com.hexaware.custodian_service.enums.MandateStatus.ACTIVE
        and mandate.effectiveFrom <= :today
        and (mandate.effectiveTo is null or mandate.effectiveTo >= :today)
        and lender.status = com.hexaware.custodian_service.enums.OrganizationStatus.ACTIVE
      """,
      countQuery = """
      select count(distinct inventory.inventoryId)
      from LenderInventory inventory, Organization lender, LenderMandate mandate
      where inventory.organizationId = lender.organizationId
        and mandate.lenderOrgId = lender.organizationId
        and mandate.custodianOrgId = :custodianId
        and mandate.status = com.hexaware.custodian_service.enums.MandateStatus.ACTIVE
        and mandate.effectiveFrom <= :today
        and (mandate.effectiveTo is null or mandate.effectiveTo >= :today)
        and lender.status = com.hexaware.custodian_service.enums.OrganizationStatus.ACTIVE
      """)
  Page<LenderInventory> findVisibleToCustodian(
      Long custodianId, LocalDate today, Pageable pageable);
}
