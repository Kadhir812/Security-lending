package com.hexaware.custodian_service.service.inventory;

import com.hexaware.custodian_service.dto.request.InventoryCreateRequest;
import com.hexaware.custodian_service.dto.request.InventoryUpdateRequest;
import com.hexaware.custodian_service.dto.response.InventoryResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.entity.LenderInventory;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LenderInventoryRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.service.setup.LendingSetupAccess;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LenderInventoryService {

  private final LenderInventoryRepository inventories;
  private final OrganizationRepository organizations;
  private final LendingSetupAccess setupAccess;
  private final Clock clock;
  private final EntityManager entityManager;

  @Transactional
  public InventoryResponse create(Authentication authentication, InventoryCreateRequest request) {
    var lender =
        setupAccess.requireLender(authentication, request.lenderOrganizationReference(), true);
    String isin = setupAccess.requireSecurity(request.securityIsin()).isin();
    if (inventories.existsByOrganizationIdAndSecurityIsin(lender.getOrganizationId(), isin)) {
      throw SetupException.conflict("Inventory already exists for this lender and security");
    }

    var inventory =
        inventories.saveAndFlush(
            LenderInventory.builder()
                .organizationId(lender.getOrganizationId())
                .securityIsin(isin)
                .totalQuantity(request.totalQuantity())
                .build());
    entityManager.refresh(inventory);
    return response(inventory, lender.getOrganizationReference());
  }

  @Transactional(readOnly = true)
  public InventoryResponse get(
      Authentication authentication, String lenderReference, String securityIsin) {
    var lender = setupAccess.requireLender(authentication, lenderReference, false);
    String isin = setupAccess.normalizeIsin(securityIsin);
    var inventory =
        inventories
            .findByOrganizationIdAndSecurityIsin(lender.getOrganizationId(), isin)
            .orElseThrow(() -> SetupException.missing("Inventory not found in your mandate scope"));
    return response(inventory, lender.getOrganizationReference());
  }

  @Transactional(readOnly = true)
  public PageResponse<InventoryResponse> list(
      Authentication authentication, int page, int size) {
    var actor = setupAccess.actor(authentication, false);
    var inventoryPage =
        inventories.findVisibleToCustodian(
            actor.getOrganizationId(),
            LocalDate.now(clock),
            SetupValidation.page(page, size, "securityIsin"));
    List<Long> lenderIds =
        inventoryPage.getContent().stream()
            .map(LenderInventory::getOrganizationId)
            .distinct()
            .toList();
    Map<Long, String> lenderReferences =
        organizations.findAllById(lenderIds).stream()
            .collect(
                Collectors.toMap(
                    organization -> organization.getOrganizationId(),
                    organization -> organization.getOrganizationReference()));
    return PageResponse.from(
        inventoryPage.map(
            inventory ->
                response(inventory, lenderReferences.get(inventory.getOrganizationId()))));
  }

  @Transactional
  public InventoryResponse update(
      Authentication authentication,
      String lenderReference,
      String securityIsin,
      InventoryUpdateRequest request) {
    var lender = setupAccess.requireLender(authentication, lenderReference, true);
    String isin = setupAccess.requireSecurity(securityIsin).isin();
    var inventory =
        inventories
            .findLocked(lender.getOrganizationId(), isin)
            .orElseThrow(() -> SetupException.missing("Inventory not found in your mandate scope"));
    SetupValidation.version(inventory.getVersion(), request.version());

    InventoryQuantityRules.requireTotalCoversEncumbrances(
        request.totalQuantity(), inventory.getOnLoanQuantity(), inventory.getReservedQuantity());
    inventory.setTotalQuantity(request.totalQuantity());
    inventories.flush();
    entityManager.refresh(inventory);
    return response(inventory, lender.getOrganizationReference());
  }

  private InventoryResponse response(LenderInventory inventory, String lenderReference) {
    return new InventoryResponse(
        lenderReference,
        inventory.getSecurityIsin(),
        inventory.getTotalQuantity(),
        inventory.getOnLoanQuantity(),
        inventory.getReservedQuantity(),
        InventoryQuantityRules.available(
            inventory.getTotalQuantity(),
            inventory.getOnLoanQuantity(),
            inventory.getReservedQuantity()),
        inventory.getVersion());
  }
}
