package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.client.ValuationCalculationResponse;
import com.hexaware.custodian_service.dto.request.EligibleSecurities;
import com.hexaware.custodian_service.entity.CollateralHaircut;
import com.hexaware.custodian_service.entity.CounterpartyCreditLimit;
import com.hexaware.custodian_service.entity.LenderInventory;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LendingFee;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.LocateRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.AssetType;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.RuleStatus;
import com.hexaware.custodian_service.enums.LoanRequestStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.CollateralHaircutRepository;
import com.hexaware.custodian_service.repository.ContractStatusHistoryRepository;
import com.hexaware.custodian_service.repository.CounterpartyCreditLimitRepository;
import com.hexaware.custodian_service.repository.InventoryReservationRepository;
import com.hexaware.custodian_service.repository.LenderInventoryRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.LocateRequestRepository;
import com.hexaware.custodian_service.repository.LendingFeeRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.quickfix.LoanApprovalFixMessageBuilder;
import com.hexaware.custodian_service.service.fees.LendingFeePolicy;
import com.hexaware.custodian_service.service.lending.LoanDecisionCommitter;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoanDecisionCommitterTest {
  private static final Instant NOW = Instant.parse("2026-11-08T05:30:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);

  @Mock SetupAccess access;
  @Mock LoanRequestRepository requests;
  @Mock LocateRequestRepository locates;
  @Mock LoanContractRepository contracts;
  @Mock InventoryReservationRepository reservations;
  @Mock ContractStatusHistoryRepository histories;
  @Mock MslaAgreementRepository agreements;
  @Mock LenderMandateRepository mandates;
  @Mock OrganizationRepository organizations;
  @Mock LenderInventoryRepository inventories;
  @Mock CounterpartyCreditLimitRepository creditLimits;
  @Mock LendingFeeRepository fees;
  @Mock CollateralHaircutRepository haircuts;
  @Mock OutgoingEventService outgoing;
  @Mock Authentication authentication;

  private LoanDecisionCommitter committer;
  private LoanRequest request;
  private MslaAgreement agreement;
  private LenderMandate mandate;
  private Organization custodian;
  private Organization lender;
  private Organization borrower;
  private LendingFee fee;
  private CollateralHaircut haircut;

  @BeforeEach
  void setUp() {
    committer =
        new LoanDecisionCommitter(
            access,
            requests,
            locates,
            contracts,
            reservations,
            histories,
            agreements,
            mandates,
            organizations,
            inventories,
            creditLimits,
            fees,
            haircuts,
            new LendingFeePolicy(),
            outgoing,
            new LoanApprovalFixMessageBuilder(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    ReflectionTestUtils.setField(committer, "maxPriceAge", Duration.ofHours(24));

    custodian = organization(50L, "CUSTODIAN-001", OrganizationType.CUSTODIAN);
    lender = organization(40L, "LENDER-001", OrganizationType.LENDER);
    borrower = organization(10L, "BORROWER-001", OrganizationType.BORROWER);
    when(access.actor(authentication, true)).thenReturn(custodian);
    request =
        LoanRequest.builder()
            .requestId(100L)
            .requestReference("LR-2026-000042")
            .mslaId(20L)
            .securityIsin("INE002A01018")
            .quantity(10L)
            .requestedSettlementDate(TODAY.plusDays(2))
            .settlementCurrency("INR")
            .status(LoanRequestStatus.PENDING)
            .version(0L)
            .build();
    agreement =
        MslaAgreement.builder()
            .mslaId(20L)
            .mandateId(30L)
            .borrowerOrgId(10L)
            .agreementStatus(AgreementStatus.ACTIVE)
            .effectiveDate(TODAY.minusDays(1))
            .expiryDate(TODAY.plusDays(20))
            .baseCurrency("INR")
            .eligibleSecurities(
                EligibleSecurities.builder().allowedTypes(List.of(AssetType.EQUITY)).build())
            .collateralType(CollateralType.CASH)
            .collateralCoveragePct(new BigDecimal("105"))
            .settlementCalendarCode("INR-SETTLEMENT-2026")
            .version(0L)
            .build();
    mandate =
        LenderMandate.builder()
            .mandateId(30L)
            .mandateReference("MANDATE-2026-001")
            .lenderOrgId(40L)
            .custodianOrgId(50L)
            .status(MandateStatus.ACTIVE)
            .effectiveFrom(TODAY.minusDays(1))
            .effectiveTo(TODAY.plusDays(20))
            .version(0L)
            .build();
    fee =
        LendingFee.builder()
            .feeId(1L)
            .mslaId(20L)
            .securityIsin(null)
            .annualFeePct(new BigDecimal("0.125000"))
            .effectiveFrom(TODAY.minusDays(1))
            .effectiveTo(TODAY.plusDays(20))
            .status(RuleStatus.ACTIVE)
            .version(0L)
            .build();
    haircut =
        CollateralHaircut.builder()
            .haircutId(2L)
            .mslaId(20L)
            .collateralType(CollateralType.CASH)
            .currency("INR")
            .haircutPercentage(BigDecimal.ZERO)
            .effectiveFrom(NOW.minusSeconds(60))
            .effectiveTo(NOW.plusSeconds(60_000))
            .version(0L)
            .build();

    when(requests.findLockedByRequestReference(request.getRequestReference()))
        .thenReturn(Optional.of(request));
    when(agreements.findById(20L)).thenReturn(Optional.of(agreement));
    when(agreements.findLocked(20L)).thenReturn(Optional.of(agreement));
    when(mandates.findById(30L)).thenReturn(Optional.of(mandate));
    when(mandates.findLocked("MANDATE-2026-001", 50L)).thenReturn(Optional.of(mandate));
    when(organizations.findById(40L)).thenReturn(Optional.of(lender));
    when(organizations.findById(10L)).thenReturn(Optional.of(borrower));
    when(organizations.findLocked("LENDER-001")).thenReturn(Optional.of(lender));
    when(organizations.findLocked("BORROWER-001")).thenReturn(Optional.of(borrower));
    when(fees.findByMslaIdOrderByEffectiveFromDescFeeIdDesc(20L))
        .thenReturn(List.of(fee));
    when(haircuts.findByMslaIdOrderByEffectiveFromDescHaircutIdDesc(20L))
        .thenReturn(List.of(haircut));
  }

  @Test
  void insufficientInventoryDoesNotReserveCreditOrCreateContract() {
    LenderInventory inventory = inventory(5L);
    when(inventories.findLocked(40L, request.getSecurityIsin()))
        .thenReturn(Optional.of(inventory));
    when(creditLimits.findLockedByMslaId(20L))
        .thenReturn(Optional.of(creditLimit(new BigDecimal("1000"))));

    assertThatThrownBy(() -> approve())
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("Insufficient available lender inventory");

    verify(inventories, never()).flush();
    verify(creditLimits, never()).flush();
    verifyNoInteractions(contracts, reservations, histories, outgoing);
  }

  @Test
  void insufficientCreditDoesNotReserveInventoryOrCreateContract() {
    LenderInventory inventory = inventory(100L);
    when(inventories.findLocked(40L, request.getSecurityIsin()))
        .thenReturn(Optional.of(inventory));
    when(creditLimits.findLockedByMslaId(20L))
        .thenReturn(Optional.of(creditLimit(new BigDecimal("50"))));

    assertThatThrownBy(() -> approve())
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("Insufficient available MSLA credit");

    verify(inventories, never()).flush();
    verify(creditLimits, never()).flush();
    verifyNoInteractions(contracts, reservations, histories, outgoing);
  }

  private void approve() {
    committer.approve(
        authentication,
        request.getRequestReference(),
        0L,
        0L,
        0L,
        fee.getFeeId(),
        0L,
        haircut.getHaircutId(),
        0L,
        AssetType.EQUITY,
        "INR",
        ValuationCalculationResponse.builder()
            .calculationReference("0cf95db6-fde9-4e06-9cb1-2dafc5ae9241")
            .securityIsin(request.getSecurityIsin())
            .quantity(request.getQuantity())
            .marketPrice(new BigDecimal("10"))
            .currency("INR")
            .priceReference("8fef347e-5096-4f97-bc23-703fdc09487e")
            .priceSource("TEST")
            .priceTimestamp(NOW.minusSeconds(10))
            .calculatedAt(NOW.minusSeconds(5))
            .loanMarketValue(new BigDecimal("100"))
            .requiredCashCollateral(new BigDecimal("105"))
            .coveragePercentage(new BigDecimal("105"))
            .cashHaircutPercentage(BigDecimal.ZERO)
            .creditReservationAmount(new BigDecimal("100"))
            .build());
  }

  private LenderInventory inventory(long totalQuantity) {
    return LenderInventory.builder()
        .inventoryId(1L)
        .organizationId(40L)
        .securityIsin(request.getSecurityIsin())
        .totalQuantity(totalQuantity)
        .onLoanQuantity(0L)
        .reservedQuantity(0L)
        .version(0L)
        .build();
  }

  private CounterpartyCreditLimit creditLimit(BigDecimal limit) {
    return CounterpartyCreditLimit.builder()
        .creditLimitId(1L)
        .mslaId(20L)
        .limitAmount(limit)
        .usedAmount(BigDecimal.ZERO)
        .reservedAmount(BigDecimal.ZERO)
        .status(RuleStatus.ACTIVE)
        .version(0L)
        .build();
  }

  private Organization organization(Long id, String reference, OrganizationType type) {
    return Organization.builder()
        .organizationId(id)
        .organizationReference(reference)
        .organizationType(type)
        .status(OrganizationStatus.ACTIVE)
        .build();
  }
}
