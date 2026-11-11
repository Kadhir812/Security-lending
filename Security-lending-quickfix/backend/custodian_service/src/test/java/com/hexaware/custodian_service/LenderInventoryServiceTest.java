package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.client.SecurityReferenceResponse;
import com.hexaware.custodian_service.dto.request.InventoryUpdateRequest;
import com.hexaware.custodian_service.entity.LenderInventory;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.AssetType;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LenderInventoryRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.service.inventory.LenderInventoryService;
import com.hexaware.custodian_service.service.setup.LendingSetupAccess;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.mockito.Mockito;

class LenderInventoryServiceTest {

  @Test
  void updateFromConcurrentStaleVersionIsRejectedBeforeMutation() {
    var inventories = Mockito.mock(LenderInventoryRepository.class);
    var organizations = Mockito.mock(OrganizationRepository.class);
    var setupAccess = Mockito.mock(LendingSetupAccess.class);
    var entityManager = Mockito.mock(EntityManager.class);
    var service =
        new LenderInventoryService(
            inventories,
            organizations,
            setupAccess,
            Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC),
            entityManager);
    var authentication = Mockito.mock(Authentication.class);
    var lender =
        Organization.builder()
            .organizationId(80L)
            .organizationReference("LENDER-A")
            .organizationType(OrganizationType.LENDER)
            .build();
    var inventory =
        LenderInventory.builder()
            .inventoryId(10L)
            .organizationId(80L)
            .securityIsin("INE002A01018")
            .totalQuantity(100L)
            .onLoanQuantity(15L)
            .reservedQuantity(5L)
            .version(4L)
            .build();
    when(setupAccess.requireLender(authentication, "LENDER-A", true)).thenReturn(lender);
    when(setupAccess.requireSecurity("INE002A01018"))
        .thenReturn(
            new SecurityReferenceResponse(
                1L,
                0L,
                "INE002A01018",
                "EXAMPLE",
                "Example Security",
                AssetType.EQUITY,
                "INR",
                "NSE",
                "ACTIVE"));
    when(inventories.findLocked(80L, "INE002A01018")).thenReturn(Optional.of(inventory));

    assertThatThrownBy(
            () ->
                service.update(
                    authentication,
                    "LENDER-A",
                    "INE002A01018",
                    new InventoryUpdateRequest(120L, 3L)))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("Version");

    verify(inventories, never()).flush();
    verify(entityManager, never()).refresh(inventory);
  }
}
