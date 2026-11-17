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
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LenderInventoryService {

  private static final Logger log = LoggerFactory.getLogger(LenderInventoryService.class);
  private static final Pattern SQL_CONSTRAINT_PATTERN =
      Pattern.compile("for key '([^']+)'", Pattern.CASE_INSENSITIVE);

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

    try {
      var inventory =
          inventories.saveAndFlush(
              LenderInventory.builder()
                  .organizationId(lender.getOrganizationId())
                  .securityIsin(isin)
                  .totalQuantity(request.totalQuantity())
                  .build());
      entityManager.refresh(inventory);
      return response(inventory, lender.getOrganizationReference());
    } catch (DataIntegrityViolationException duplicate) {
      String constraintName = findConstraintName(duplicate);
      String sqlState = findSqlState(duplicate);
      Integer mysqlErrorCode = findMysqlErrorCode(duplicate);
      String message =
          duplicate.getMostSpecificCause() != null
              ? duplicate.getMostSpecificCause().getMessage()
              : duplicate.getMessage();
      boolean foreignKeyViolation = isForeignKeyViolation(constraintName, sqlState, message);
      log.warn(
          "Lender inventory persistence failed: action=create, constraintName={}, sqlState={}, mysqlErrorCode={}, foreignKeyViolation={}, likelyForeignKeyMismatch={}, message={}",
          constraintName,
          sqlState,
          mysqlErrorCode,
          foreignKeyViolation,
          foreignKeyViolation,
          message,
          duplicate);
      if (foreignKeyViolation) {
        log.error(
            "Lender inventory foreign-key mismatch detected: likely bad lender organization reference or missing/security ISIN in live MySQL. table=lender_inventory, constraintName={}, sqlState={}, mysqlErrorCode={}, message={}, rootCause={}",
            constraintName,
            sqlState,
            mysqlErrorCode,
            message,
            duplicate.getMostSpecificCause() != null ? duplicate.getMostSpecificCause() : duplicate,
            duplicate);
      }
      if (isLenderInventoryDuplicateViolation(constraintName, sqlState, duplicate)) {
        throw SetupException.conflict("Inventory already exists for this lender and security");
      }
      throw duplicate;
    }
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

  private boolean isForeignKeyViolation(String constraintName, String sqlState, String message) {
    String normalizedConstraint = constraintName == null ? "" : constraintName.toLowerCase(Locale.ROOT);
    String normalizedMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
    return normalizedConstraint.contains("fk_")
        || normalizedConstraint.contains("foreign_key")
        || normalizedMessage.contains("foreign key")
        || normalizedMessage.contains("cannot add or update a child row")
        || ("23000".equalsIgnoreCase(sqlState) && normalizedMessage.contains("foreign key constraint"));
  }

  private boolean isLenderInventoryDuplicateViolation(
      String constraintName, String sqlState, DataIntegrityViolationException duplicate) {
    String normalizedConstraint = constraintName == null ? "" : constraintName.toLowerCase(Locale.ROOT);
    String normalizedMessage =
        duplicate.getMostSpecificCause() != null
            ? duplicate.getMostSpecificCause().getMessage()
            : duplicate.getMessage();
    String normalizedMessageText = normalizedMessage == null ? "" : normalizedMessage.toLowerCase(Locale.ROOT);

    boolean matchesConstraint =
        normalizedConstraint.contains("uk_lender_inventory_org_isin")
            || (normalizedConstraint.contains("lender_inventory")
                && normalizedConstraint.contains("organization"))
            || (normalizedConstraint.contains("organization")
                && normalizedConstraint.contains("security_isin"));

    boolean matchesMessage =
        normalizedMessageText.contains("duplicate entry")
            && normalizedMessageText.contains("lender_inventory")
            && (normalizedMessageText.contains("organization_id")
                || normalizedMessageText.contains("security_isin")
                || normalizedMessageText.contains("uk_lender_inventory_org_isin"));

    if (matchesConstraint) {
      return true;
    }

    return ("23000".equalsIgnoreCase(sqlState) || "23505".equalsIgnoreCase(sqlState))
        && matchesMessage;
  }

  private String findConstraintName(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolationException) {
        String constraintName = constraintViolationException.getConstraintName();
        if (constraintName != null && !constraintName.isBlank()) {
          return constraintName;
        }
      }
      if (current instanceof SQLException sqlException) {
        Matcher matcher =
            SQL_CONSTRAINT_PATTERN.matcher(
                sqlException.getMessage() == null ? "" : sqlException.getMessage());
        if (matcher.find()) {
          return matcher.group(1);
        }
      }
      current = current.getCause();
    }
    return null;
  }

  private String findSqlState(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
        return sqlException.getSQLState();
      }
      if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolationException
          && constraintViolationException.getSQLState() != null) {
        return constraintViolationException.getSQLState();
      }
      current = current.getCause();
    }
    return null;
  }

  private Integer findMysqlErrorCode(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLException sqlException && sqlException.getErrorCode() != 0) {
        return sqlException.getErrorCode();
      }
      if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolationException
          && constraintViolationException.getErrorCode() != 0) {
        return constraintViolationException.getErrorCode();
      }
      current = current.getCause();
    }
    return null;
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