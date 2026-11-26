package com.hexaware.custodian_service.kafka.consumer;

import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.LoanContract;
import com.hexaware.custodian_service.entity.LoanRequest;
import com.hexaware.custodian_service.entity.MarginCall;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.enums.LoanContractStatus;
import com.hexaware.custodian_service.enums.MarginCallStatus;
import com.hexaware.custodian_service.enums.SettlementInstructionStatus;
import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.kafka.event.CollateralInstructionSubmittedPayload;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.repository.CollateralRepository;
import com.hexaware.custodian_service.repository.LenderMandateRepository;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.repository.MarginCallRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.SettlementInstructionRepository;
import com.hexaware.custodian_service.service.messaging.OutgoingEventService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class CollateralInstructionSubmittedHandler implements MessageHandler {
  private final ObjectMapper mapper;
  private final LoanContractRepository contracts;
  private final LoanRequestRepository requests;
  private final MslaAgreementRepository agreements;
  private final LenderMandateRepository mandates;
  private final OrganizationRepository organizations;
  private final CollateralRepository collaterals;
  private final SettlementInstructionRepository settlements;
  private final MarginCallRepository marginCalls;
  private final OutgoingEventService outgoing;
  private final Clock clock;

  @Override
  public String eventType() {
    return "COLLATERAL_INSTRUCTION_SUBMITTED";
  }

  @Override
  public void handle(EventEnvelope event) {
    CollateralInstructionSubmittedPayload payload =
        mapper.convertValue(event.getPayload(), CollateralInstructionSubmittedPayload.class);
    validateEnvelope(event, payload);
    LoanContract contract = contracts.findByTransactionReference(payload.transactionReference())
        .orElseThrow(() -> invalid("LOAN_CONTRACT_NOT_FOUND"));
    LoanRequest request = requests.findById(contract.getLoanRequestId())
        .orElseThrow(() -> invalid("LOAN_REQUEST_NOT_FOUND"));
    MslaAgreement agreement = agreements.findById(request.getMslaId())
        .orElseThrow(() -> invalid("MSLA_NOT_FOUND"));
    LenderMandate mandate = mandates.findById(agreement.getMandateId())
        .orElseThrow(() -> invalid("MANDATE_NOT_FOUND"));
    Organization borrower = organizations.findById(agreement.getBorrowerOrgId())
        .orElseThrow(() -> invalid("BORROWER_NOT_FOUND"));
    Organization custodian = organizations.findById(mandate.getCustodianOrgId())
        .orElseThrow(() -> invalid("CUSTODIAN_NOT_FOUND"));
    if (!borrower.getOrganizationReference().equals(payload.borrowerOrganizationReference())
        || !custodian.getOrganizationReference().equals(payload.custodianOrganizationReference())) {
      throw invalid("COLLATERAL_INSTRUCTION_PARTIES_MISMATCH");
    }

    MarginCall call = null;
    if (payload.marginCallReference() == null) {
      if (contract.getContractStatus() != LoanContractStatus.APPROVED
          && contract.getContractStatus() != LoanContractStatus.PENDING_SETTLEMENT) {
        throw invalid("CONTRACT_NOT_ELIGIBLE_FOR_INITIAL_COLLATERAL");
      }
    } else {
      if (contract.getContractStatus() != LoanContractStatus.ON_LOAN) {
        throw invalid("CONTRACT_NOT_ON_LOAN");
      }
      call = marginCalls.findByMarginCallReference(payload.marginCallReference())
          .orElseThrow(() -> invalid("MARGIN_CALL_NOT_FOUND"));
      if (!call.getContractId().equals(contract.getContractId())
          || !List.of(
                  MarginCallStatus.OPEN,
                  MarginCallStatus.PARTIALLY_SATISFIED,
                  MarginCallStatus.BREACHED)
              .contains(call.getStatus())) {
        throw invalid("MARGIN_CALL_NOT_ACTIVE_FOR_TOP_UP");
      }
    }
    if (!contract.getSettlementCurrency().equals(payload.currency())) {
      throw invalid("COLLATERAL_CURRENCY_MISMATCH");
    }
    if (!"CASH".equals(String.valueOf(contract.getCollateralTerms().get("collateralType")))
        || !Objects.equals(contract.getSettlementCurrency(),
            contract.getCollateralTerms().get("currency"))
        || decimal(contract.getCollateralTerms().get("haircutPercentage")).signum() != 0) {
      throw invalid("CONTRACT_CASH_TERMS_MISMATCH");
    }

    Collateral collateral = collaterals.findByCollateralReference(payload.instructionReference())
        .orElse(null);
    if (collateral != null) {
      if (!collateral.getContractId().equals(contract.getContractId())
          || collateral.getPostedCashAmount().compareTo(payload.amount()) != 0
          || !payload.currency().equals(collateral.getCurrency())
          || collateral.getCollateralType() != CollateralType.CASH) {
        throw invalid("COLLATERAL_INSTRUCTION_REFERENCE_REUSED");
      }
    } else {
      collateral = collaterals.save(Collateral.builder()
          .collateralReference(payload.instructionReference())
          .contractId(contract.getContractId())
          .collateralType(CollateralType.CASH)
          .currency(payload.currency())
          .postedCashAmount(payload.amount())
          .appliedHaircutPct(BigDecimal.ZERO)
          .status("PENDING")
          .build());
    }

    if (call != null) {
      SettlementInstruction instruction =
          settlements.findByInstructionReference(payload.instructionReference()).orElse(null);
      if (instruction != null) {
        if (!Objects.equals(instruction.getContractId(), contract.getContractId())
            || !Objects.equals(instruction.getCollateralId(), collateral.getCollateralId())
            || !Objects.equals(instruction.getMarginCallId(), call.getMarginCallId())
            || instruction.getAmount().compareTo(payload.amount()) != 0
            || !payload.currency().equals(instruction.getCurrency())) {
          throw invalid("TOP_UP_INSTRUCTION_REFERENCE_REUSED");
        }
      } else {
        settlements.save(SettlementInstruction.builder()
            .instructionReference(payload.instructionReference())
            .contractId(contract.getContractId())
            .collateralId(collateral.getCollateralId())
            .marginCallId(call.getMarginCallId())
            .instructionType("COLLATERAL_TRANSFER")
            .fromOrganizationId(borrower.getOrganizationId())
            .toOrganizationId(custodian.getOrganizationId())
            .fromAccount("BORROWER-CASH")
            .toAccount("CUSTODIAN-CASH")
            .amount(payload.amount())
            .currency(payload.currency())
            .status(SettlementInstructionStatus.PENDING)
            .settlementDate(clock.instant().atZone(ZoneOffset.UTC).toLocalDate())
            .createdAt(clock.instant())
            .build());
      }
    }
    publishReceipt(event, payload);
  }

  private void publishReceipt(
      EventEnvelope source, CollateralInstructionSubmittedPayload payload) {
    Map<String, Object> response = new java.util.LinkedHashMap<>();
    response.put("payloadVersion", 1);
    response.put("transactionReference", payload.transactionReference());
    response.put("instructionReference", payload.instructionReference());
    response.put("borrowerOrganizationReference", payload.borrowerOrganizationReference());
    response.put("custodianOrganizationReference", payload.custodianOrganizationReference());
    response.put("amount", payload.amount().toPlainString());
    response.put("currency", payload.currency());
    response.put("marginCallReference", payload.marginCallReference());
    response.put("instructionStatus", "PENDING");
    outgoing.enqueue("COLLATERAL_INSTRUCTION",
        EventEnvelope.builder()
            .eventId(java.util.UUID.randomUUID().toString())
            .eventType("COLLATERAL_INSTRUCTION_RECEIVED")
            .schemaVersion(1)
            .producerService("CUSTODIAN")
            .destinationService("BORROWER")
            .correlationId(payload.instructionReference())
            .causationEventId(source.getEventId())
            .aggregateReference(payload.instructionReference())
            .aggregateVersion(source.getAggregateVersion())
            .occurredAt(clock.instant())
            .senderOrganizationReference(payload.custodianOrganizationReference())
            .receiverOrganizationReference(payload.borrowerOrganizationReference())
            .payload(response)
            .build());
  }

  private BigDecimal decimal(Object value) {
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw invalid("CONTRACT_CASH_TERMS_INVALID");
    }
  }

  private void validateEnvelope(
      EventEnvelope event, CollateralInstructionSubmittedPayload payload) {
    if (payload == null
        || !"BORROWER".equals(event.getProducerService())
        || !"CUSTODIAN".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || payload.payloadVersion() == null
        || payload.payloadVersion() != 1
        || !Objects.equals(payload.instructionReference(), event.getAggregateReference())
        || !Objects.equals(payload.instructionReference(), event.getCorrelationId())
        || !Objects.equals(payload.borrowerOrganizationReference(), event.getSenderOrganizationReference())
        || !Objects.equals(payload.custodianOrganizationReference(), event.getReceiverOrganizationReference())
        || payload.transactionReference() == null
        || payload.transactionReference().isBlank()
        || payload.instructionReference() == null
        || payload.instructionReference().isBlank()
        || payload.amount() == null
        || payload.amount().signum() <= 0
        || !"CASH".equals(payload.collateralType())
        || payload.currency() == null
        || !payload.currency().matches("[A-Z]{3}")) {
      throw invalid("COLLATERAL_INSTRUCTION_ENVELOPE_MISMATCH");
    }
  }

  private InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
