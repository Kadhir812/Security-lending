package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.hexaware.custodian_service.repository.InventoryReservationRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.service.inventory.LenderInventoryService;
import com.hexaware.custodian_service.service.setup.LendingSetupAccess;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.mockito.Mockito;

class LenderInventoryServiceTest {

  @Test
  void schemaGuardRejectsMissingUniqueIndexOnLenderInventory() {
    var jdbc = Mockito.mock(JdbcTemplate.class);
    when(jdbc.queryForObject(
            Mockito.contains("information_schema.table_constraints"),
            Mockito.eq(Integer.class),
            Mockito.any()))
        .thenReturn(0);

    var guard = new com.hexaware.custodian_service.config.LenderInventoryDatabaseGuard(jdbc);

    assertThatThrownBy(guard::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("lender_inventory")
        .hasMessageContaining("organization_id")
        .hasMessageContaining("security_isin");
  }

  @Test
  void createPersistsSuccessfulInventory() {
    var inventories = Mockito.mock(LenderInventoryRepository.class);
    var reservations = Mockito.mock(InventoryReservationRepository.class);
    var organizations = Mockito.mock(OrganizationRepository.class);
    var setupAccess = Mockito.mock(LendingSetupAccess.class);
    var entityManager = Mockito.mock(EntityManager.class);
    var service =
        new LenderInventoryService(
            inventories,
            reservations,
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
    var saved =
        LenderInventory.builder()
            .inventoryId(99L)
            .organizationId(80L)
            .securityIsin("INE002A01018")
            .totalQuantity(1000L)
            .version(0L)
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
    when(inventories.existsByOrganizationIdAndSecurityIsin(80L, "INE002A01018")).thenReturn(false);
    when(inventories.saveAndFlush(any())).thenReturn(saved);

    var response =
        service.create(
            authentication,
            new com.hexaware.custodian_service.dto.request.InventoryCreateRequest(
                "LENDER-A", "INE002A01018", 1000L));

    assertThat(response.lenderOrganizationReference()).isEqualTo("LENDER-A");
    assertThat(response.securityIsin()).isEqualTo("INE002A01018");
    assertThat(response.totalQuantity()).isEqualTo(1000L);
    verify(inventories).saveAndFlush(any());
  }

  @Test
  void duplicateCreateRejectedBeforePersistsAnotherRow() {
    var inventories = Mockito.mock(LenderInventoryRepository.class);
    var reservations = Mockito.mock(InventoryReservationRepository.class);
    var organizations = Mockito.mock(OrganizationRepository.class);
    var setupAccess = Mockito.mock(LendingSetupAccess.class);
    var entityManager = Mockito.mock(EntityManager.class);
    var service =
        new LenderInventoryService(
            inventories,
            reservations,
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
    when(inventories.existsByOrganizationIdAndSecurityIsin(80L, "INE002A01018")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    authentication,
                    new com.hexaware.custodian_service.dto.request.InventoryCreateRequest(
                        "LENDER-A", "INE002A01018", 1000L)))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("already exists");

    verify(inventories, never()).saveAndFlush(any());
  }

  @Test
  void unrelatedDatabaseConstraintFailureIsNotMaskedAsDuplicateInventory() {
    var inventories = Mockito.mock(LenderInventoryRepository.class);
    var reservations = Mockito.mock(InventoryReservationRepository.class);
    var organizations = Mockito.mock(OrganizationRepository.class);
    var setupAccess = Mockito.mock(LendingSetupAccess.class);
    var entityManager = Mockito.mock(EntityManager.class);
    var service =
        new LenderInventoryService(
            inventories,
            reservations,
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
    when(inventories.existsByOrganizationIdAndSecurityIsin(80L, "INE002A01018")).thenReturn(false);
    when(inventories.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "other constraint violation",
                new SQLException("Duplicate entry 'X' for key 'other_constraint'", "23000")));

    assertThatThrownBy(
            () ->
                service.create(
                    authentication,
                    new com.hexaware.custodian_service.dto.request.InventoryCreateRequest(
                        "LENDER-A", "INE002A01018", 1000L)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("other constraint violation");
  }

  @Test
  void updateFromConcurrentStaleVersionIsRejectedBeforeMutation() {
    var inventories = Mockito.mock(LenderInventoryRepository.class);
    var reservations = Mockito.mock(InventoryReservationRepository.class);
    var organizations = Mockito.mock(OrganizationRepository.class);
    var setupAccess = Mockito.mock(LendingSetupAccess.class);
    var entityManager = Mockito.mock(EntityManager.class);
    var service =
        new LenderInventoryService(
            inventories,
            reservations,
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
