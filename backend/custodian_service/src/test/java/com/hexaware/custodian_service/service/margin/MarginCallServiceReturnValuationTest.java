package com.hexaware.custodian_service.service.margin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.client.MtmCalculationRequest;
import com.hexaware.custodian_service.client.MtmCalculationResponse;
import com.hexaware.custodian_service.client.ValuationCalculationClient;
import com.hexaware.custodian_service.client.ValuationServiceTokenProvider;
import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.repository.CollateralRepository;
import com.hexaware.custodian_service.repository.CreditExposureHistoryRepository;
import com.hexaware.custodian_service.repository.CounterpartyCreditLimitRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MarginCallRepository;
import com.hexaware.custodian_service.repository.MarginCallTransactionLogRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class MarginCallServiceReturnValuationTest {
  private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
  private static final Instant RETURN_CREATED_AT = NOW.minusSeconds(300);

  @Mock private SetupAccess access;
  @Mock private LoanContractRepository contracts;
  @Mock private LoanRequestRepository requests;
  @Mock private MslaAgreementRepository agreements;
  @Mock private LenderMandateRepository mandates;
  @Mock private OrganizationRepository organizations;
  @Mock private SettlementInstructionRepository settlements;
  @Mock private CollateralRepository collaterals;
  @Mock private MarginCallRepository calls;
  @Mock private MarginCallTransactionLogRepository callLogs;
  @Mock private CounterpartyCreditLimitRepository creditLimits;
  @Mock private CreditExposureHistoryRepository exposureHistory;
  @Mock private ValuationCalculationClient mtm;
  @Mock private ValuationServiceTokenProvider serviceTokens;
  @Mock private OutgoingEventService outgoing;
  @Mock private PlatformTransactionManager transactionManager;

  private MarginCallService service;

  @BeforeEach
  void setUp() {
    service = new MarginCallService(
        access,
        contracts,
        requests,
        agreements,
        mandates,
        organizations,
        settlements,
        collaterals,
        calls,
        callLogs,
        creditLimits,
        exposureHistory,
        mtm,
        serviceTokens,
        outgoing,
        Clock.fixed(NOW, ZoneOffset.UTC),
        transactionManager);

    when(requests.findById(30L)).thenReturn(Optional.of(loanRequest()));
    when(agreements.findById(40L)).thenReturn(Optional.of(
        MslaAgreement.builder().mslaId(40L).mandateId(50L).borrowerOrgId(2L).build()));
    when(mandates.findById(50L)).thenReturn(Optional.of(
        LenderMandate.builder().mandateId(50L).custodianOrgId(1L).build()));
    when(organizations.findById(1L)).thenReturn(Optional.of(
        organization(1L, "CUSTODIAN-001")));
    when(organizations.findById(2L)).thenReturn(Optional.of(
        organization(2L, "BORROWER-001")));
    when(collaterals.findByContractId(20L)).thenReturn(List.of());
    when(serviceTokens.token("CUSTODIAN-001")).thenReturn("service-token");
    when(mtm.calculateMtm(any(MtmCalculationRequest.class), eq("service-token")))
        .thenAnswer(invocation -> response(invocation.getArgument(0)));
  }

  @Test
  void reusesReturnValuationReferenceForRetriesAndChangesItWhenInputsChange() {
    LoanContract contract = contract();

    service.calculateReturnValuation(
        contract, 90L, "RET-001", RETURN_CREATED_AT);
    service.calculateReturnValuation(
        contract, 90L, "RET-001", RETURN_CREATED_AT);

    Collateral cashCollateral = Collateral.builder()
        .collateralType(CollateralType.CASH)
        .status("POSTED")
        .postedCashAmount(new BigDecimal("500.00"))
        .build();
    when(collaterals.findByContractId(20L)).thenReturn(List.of(cashCollateral));
    service.calculateReturnValuation(
        contract, 90L, "RET-001", RETURN_CREATED_AT);

    ArgumentCaptor<MtmCalculationRequest> captor =
        ArgumentCaptor.forClass(MtmCalculationRequest.class);
    verify(mtm, times(3)).calculateMtm(captor.capture(), eq("service-token"));
    List<MtmCalculationRequest> requests = captor.getAllValues();

    assertThat(requests.get(0).getInputAsOf()).isEqualTo(RETURN_CREATED_AT);
    assertThat(requests.get(1).getInputAsOf()).isEqualTo(RETURN_CREATED_AT);
    assertThat(requests.get(0).getCalculationRequestReference())
        .isEqualTo(requests.get(1).getCalculationRequestReference());
    assertThat(requests.get(2).getCalculationRequestReference())
        .isNotEqualTo(requests.get(0).getCalculationRequestReference());
  }

  private LoanContract contract() {
    return LoanContract.builder()
        .contractId(20L)
        .transactionReference("TX-001")
        .loanRequestId(30L)
        .outstandingQuantity(100L)
        .version(7L)
        .settlementCurrency("INR")
        .collateralCoveragePct(new BigDecimal("105.0000"))
        .latestValuationAt(NOW.minusSeconds(600))
        .build();
  }

  private LoanRequest loanRequest() {
    return LoanRequest.builder()
        .requestId(30L)
        .mslaId(40L)
        .securityIsin("INE002A01018")
        .settlementCurrency("INR")
        .build();
  }

  private Organization organization(Long id, String reference) {
    return Organization.builder()
        .organizationId(id)
        .organizationReference(reference)
        .build();
  }

  private MtmCalculationResponse response(MtmCalculationRequest request) {
    MtmCalculationResponse result = new MtmCalculationResponse();
    result.setCalculationReference("VALUATION-001");
    result.setCalculationRequestReference(request.getCalculationRequestReference());
    result.setTransactionReference(request.getTransactionReference());
    result.setSecurityIsin(request.getSecurityIsin());
    result.setOutstandingQuantity(request.getOutstandingQuantity());
    result.setMarketPrice(new BigDecimal("105.00"));
    result.setCurrency(request.getCurrency());
    result.setPriceTimestamp(NOW);
    result.setCalculatedAt(NOW.plusSeconds(1));
    result.setLoanMarketValue(new BigDecimal("9450.00"));
    result.setRequiredCashCollateral(new BigDecimal("9922.50"));
    result.setConfirmedCashCollateral(request.getConfirmedCashCollateral());
    result.setShortfallAmount(BigDecimal.ZERO);
    result.setCoveragePercentage(request.getCoveragePercentage());
    result.setCashHaircutPercentage(BigDecimal.ZERO);
    result.setInputAsOf(request.getInputAsOf());
    result.setInputVersion(request.getInputVersion());
    result.setCustodianOrganizationReference(request.getCustodianOrganizationReference());
    result.setBorrowerOrganizationReference(request.getBorrowerOrganizationReference());
    return result;
  }
}
