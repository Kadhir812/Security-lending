package com.hexaware.custodian_service.service.fees;

import com.hexaware.custodian_service.dto.request.SimulatedFeePaymentConfirmationRequest;
import com.hexaware.custodian_service.dto.request.SimulatedFeePaymentFailureRequest;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LendingFeePayment;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LendingFeePaymentRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LendingFeePaymentService {
  private static final String OBLIGATION_PREFIX = "LENDING_FEE:";
  private final SetupAccess access;
  private final LoanContractRepository contracts;
  private final LoanRequestRepository requests;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final LendingFeePaymentRepository payments;
  private final Clock clock;
  private final LendingFeePaymentSupport support;

  @Transactional
  public void receiveSubmitted(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    String transactionReference = support.text(payload.get("transactionReference"));
    String paymentReference = support.text(payload.get("paymentReference"));
    String borrowerReference = support.text(payload.get("borrowerOrganizationReference"));
    String custodianReference = support.text(payload.get("custodianOrganizationReference"));
    if (!"FEE_PAYMENT_SUBMITTED".equals(event.getEventType())
        || !"BORROWER".equals(event.getProducerService())
        || !"CUSTODIAN".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || support.number(payload.get("payloadVersion")) != 1
        || support.number(event.getAggregateVersion()) != 0
        || !Objects.equals(event.getAggregateReference(), paymentReference)
        || !Objects.equals(event.getCorrelationId(), paymentReference)
        || !Objects.equals(event.getSenderOrganizationReference(), borrowerReference)
        || !Objects.equals(event.getReceiverOrganizationReference(), custodianReference)
        || event.getOccurredAt() == null
        || event.getOccurredAt().isAfter(clock.instant())
        || transactionReference == null
        || paymentReference == null
        || borrowerReference == null
        || custodianReference == null) {
      throw new InvalidEnvelopeException("FEE_PAYMENT_ENVELOPE_MISMATCH");
    }
    LoanContract contract = lockedContract(transactionReference);
    FeeContractParties parties = parties(contract);
    if (!Objects.equals(borrowerReference, parties.borrowerReference())
        || !Objects.equals(custodianReference, parties.custodianReference())
        || !Objects.equals(event.getSenderOrganizationReference(), parties.borrowerReference())
        || !Objects.equals(event.getReceiverOrganizationReference(), parties.custodianReference())
        || contract.getContractStatus() != LoanContractStatus.ON_LOAN) {
      throw new InvalidEnvelopeException("FEE_PAYMENT_PARTIES_OR_STATE_MISMATCH");
    }
    requireActiveParties(parties);
    List<LendingFeePayment> existing = payments.findLockedByContractId(contract.getContractId());
    List<LendingFeePayment> sameRequest = existing.stream()
        .filter(row -> paymentReference.equals(
            row.getCalculationSnapshot().get("paymentRequestReference")))
        .toList();
    String requestHash = support.requestHash(payload);
    if (!sameRequest.isEmpty()) {
      if (sameRequest.stream().anyMatch(row ->
          !requestHash.equals(row.getCalculationSnapshot().get("requestHash")))) {
        throw new InvalidEnvelopeException("FEE_PAYMENT_REFERENCE_REUSED");
      }
      return;
    }

    BigDecimal amount = support.positiveAmount(payload.get("amount"));
    String currency = support.text(payload.get("currency"));
    if (!contract.getSettlementCurrency().equals(currency)) {
      throw new InvalidEnvelopeException("FEE_PAYMENT_CURRENCY_MISMATCH");
    }
    List<Map<String, Object>> allocations = support.allocationList(payload.get("allocations"));
    BigDecimal allocated = allocations.stream()
        .map(row -> support.positiveAmount(row.get("amount")))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (allocated.compareTo(amount) != 0) {
      throw new InvalidEnvelopeException("FEE_PAYMENT_ALLOCATION_TOTAL_MISMATCH");
    }
    support.validateAvailableAllocations(contract, currency, allocations, existing);

    String obligationReference = UUID.nameUUIDFromBytes(
        (OBLIGATION_PREFIX + transactionReference).getBytes(StandardCharsets.UTF_8)).toString();
    for (int index = 0; index < allocations.size(); index++) {
      Map<String, Object> allocation = allocations.get(index);
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("paymentRequestReference", paymentReference);
      snapshot.put("requestHash", requestHash);
      snapshot.put("chargeItemKey", allocation.get("itemKey"));
      snapshot.put("sourceEventId", event.getEventId());
      snapshot.put("borrowerOrganizationReference", borrowerReference);
      snapshot.put("custodianOrganizationReference", custodianReference);
      snapshot.put("lenderOrganizationReference", parties.lenderReference());
      snapshot.put("allAllocations", allocations);
      snapshot.put("aggregateVersion", 1);
      snapshot.put("simulatedPaymentAdapter", true);
      payments.save(LendingFeePayment.builder()
          .contractId(contract.getContractId())
          .obligationReference(obligationReference)
          .calculationSnapshot(snapshot)
          .paymentReference(support.allocationPaymentReference(
              paymentReference, allocation.get("calculationResultReference"), index))
          .calculationResultReference(
              support.text(allocation.get("calculationResultReference")))
          .fromOrganizationId(parties.borrowerId())
          .toOrganizationId(parties.lenderId())
          .paymentAmount(support.positiveAmount(allocation.get("amount")))
          .currency(currency)
          .status("PENDING")
          .build());
    }
    payments.flush();
    support.enqueueUpdate(event, contract, parties, paymentReference, "FEE_PAYMENT_RECEIVED", "PENDING",
        amount, currency, allocations, 1, null, null);
  }

  @Transactional
  public Map<String, Object> confirm(
      Authentication authentication,
      String transactionReference,
      String paymentReference,
      SimulatedFeePaymentConfirmationRequest request) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    FeeContractParties parties = parties(contract);
    requireCustodian(actor, parties);
    List<LendingFeePayment> rows = support.paymentGroup(contract, paymentReference);
    String externalReference = request.getExternalConfirmationReference();
    List<LendingFeePayment> paid = rows.stream()
        .filter(row -> "PAID".equals(row.getStatus()))
        .toList();
    if (!paid.isEmpty()) {
      boolean sameConfirmation = rows.stream().allMatch(row ->
          "PAID".equals(row.getStatus())
              && Objects.equals(
                  row.getCalculationSnapshot().get("confirmationReference"), externalReference));
      if (sameConfirmation) return support.paymentResponse(paymentReference, rows, true);
      throw SetupException.conflict("Payment already has a different confirmation");
    }
    if (rows.stream().anyMatch(row -> !"PENDING".equals(row.getStatus()))) {
      throw SetupException.conflict("Only pending payment instructions can be confirmed");
    }
    var external = payments.findByExternalReference(externalReference);
    if (external.isPresent()
        && !paymentReference.equals(
            external.get().getCalculationSnapshot().get("paymentRequestReference"))) {
      throw SetupException.conflict("External confirmation reference was reused");
    }
    BigDecimal amount = rows.stream()
        .map(LendingFeePayment::getPaymentAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    List<Map<String, Object>> allocations = support.paymentAllocations(rows);
    for (int index = 0; index < rows.size(); index++) {
      LendingFeePayment row = rows.get(index);
      row.setStatus("PAID");
      row.setPaidAt(clock.instant());
      if (index == 0) row.setExternalReference(externalReference);
      Map<String, Object> snapshot = new LinkedHashMap<>(row.getCalculationSnapshot());
      snapshot.put("confirmationReference", externalReference);
      snapshot.put("aggregateVersion", 2);
      snapshot.put("confirmedAt", clock.instant().toString());
      row.setCalculationSnapshot(snapshot);
    }
    try {
      payments.saveAllAndFlush(rows);
    } catch (DataIntegrityViolationException exception) {
      throw SetupException.conflict("External confirmation reference was reused");
    }
    support.enqueueUpdate(null, contract, parties, paymentReference, "FEE_PAYMENT_UPDATED", "PAID",
        amount, rows.get(0).getCurrency(), allocations, 2, externalReference, null);
    return support.paymentResponse(paymentReference, rows, true);
  }

  @Transactional
  public Map<String, Object> fail(
      Authentication authentication,
      String transactionReference,
      String paymentReference,
      SimulatedFeePaymentFailureRequest request) {
    Organization actor = access.actor(authentication, true);
    LoanContract contract = lockedContract(transactionReference);
    FeeContractParties parties = parties(contract);
    requireCustodian(actor, parties);
    List<LendingFeePayment> rows = support.paymentGroup(contract, paymentReference);
    if (rows.stream().allMatch(row -> "FAILED".equals(row.getStatus())
        && Objects.equals(row.getFailureReason(), request.getReason()))) {
      return support.paymentResponse(paymentReference, rows, true);
    }
    if (rows.stream().anyMatch(row -> !"PENDING".equals(row.getStatus()))) {
      throw SetupException.conflict("Only pending payment instructions can fail");
    }
    BigDecimal amount = rows.stream()
        .map(LendingFeePayment::getPaymentAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    List<Map<String, Object>> allocations = support.paymentAllocations(rows);
    for (LendingFeePayment row : rows) {
      row.setStatus("FAILED");
      row.setFailureReason(request.getReason());
      Map<String, Object> snapshot = new LinkedHashMap<>(row.getCalculationSnapshot());
      snapshot.put("aggregateVersion", 2);
      snapshot.put("failureReason", request.getReason());
      snapshot.put("failedAt", clock.instant().toString());
      row.setCalculationSnapshot(snapshot);
    }
    payments.saveAllAndFlush(rows);
    support.enqueueUpdate(null, contract, parties, paymentReference, "FEE_PAYMENT_UPDATED", "FAILED",
        amount, rows.get(0).getCurrency(), allocations, 2, null, request.getReason());
    return support.paymentResponse(paymentReference, rows, true);
  }

  public List<Map<String, Object>> list(
      Authentication authentication, String transactionReference) {
    Organization actor = access.actor(authentication, false);
    LoanContract contract = contracts.findByTransactionReference(transactionReference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
    requireCustodian(actor, parties(contract));
    Map<String, List<LendingFeePayment>> grouped =
        payments.findByContractIdOrderByPaymentId(contract.getContractId()).stream()
            .collect(Collectors.groupingBy(
                row -> support.text(row.getCalculationSnapshot().get("paymentRequestReference")),
                LinkedHashMap::new,
                Collectors.toList()));
    return grouped.entrySet().stream()
        .map(entry -> support.paymentResponse(entry.getKey(), entry.getValue(), false))
        .toList();
  }

  private LoanContract lockedContract(String transactionReference) {
    if (transactionReference == null) {
      throw new InvalidEnvelopeException("FEE_PAYMENT_CONTRACT_REQUIRED");
    }
    return contracts.findLockedByTransactionReference(transactionReference)
        .orElseThrow(() -> SetupException.missing("Loan contract not found"));
  }

  private FeeContractParties parties(LoanContract contract) {
    LoanRequest request = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> SetupException.missing("Loan request not found"));
    MslaAgreement agreement = agreements.findById(request.getMslaId())
        .orElseThrow(() -> SetupException.missing("MSLA not found"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> SetupException.missing("Lender mandate not found"));
    Organization borrower = organizations.findById(agreement.getBorrowerOrgId())
        .orElseThrow(() -> SetupException.missing("Borrower organization not found"));
    Organization lender = organizations.findById(mandate.getLenderOrgId())
        .orElseThrow(() -> SetupException.missing("Lender organization not found"));
    Organization custodian = organizations.findById(mandate.getCustodianOrgId())
        .orElseThrow(() -> SetupException.missing("Custodian organization not found"));
    return new FeeContractParties(
        borrower.getOrganizationId(),
        borrower.getOrganizationReference(),
        lender.getOrganizationId(),
        lender.getOrganizationReference(),
        custodian.getOrganizationId(),
        custodian.getOrganizationReference());
  }

  private void requireActiveParties(FeeContractParties parties) {
    Organization borrower = organizations.findById(parties.borrowerId()).orElseThrow();
    Organization lender = organizations.findById(parties.lenderId()).orElseThrow();
    Organization custodian = organizations.findById(parties.custodianId()).orElseThrow();
    if (borrower.getStatus() != OrganizationStatus.ACTIVE
        || borrower.getOrganizationType() != OrganizationType.BORROWER
        || lender.getStatus() != OrganizationStatus.ACTIVE
        || lender.getOrganizationType() != OrganizationType.LENDER
        || custodian.getStatus() != OrganizationStatus.ACTIVE
        || custodian.getOrganizationType() != OrganizationType.CUSTODIAN) {
      throw new InvalidEnvelopeException("FEE_PAYMENT_PARTY_INACTIVE");
    }
  }

  private void requireCustodian(Organization actor, FeeContractParties parties) {
    if (!Objects.equals(actor.getOrganizationId(), parties.custodianId())) {
      throw SetupException.missing("Loan contract not found in this Custodian scope");
    }
  }

}
