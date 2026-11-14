package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.CashCollateralInstructionRequest;
import com.hexaware.custodian_service.dto.request.SimulatedSettlementConfirmationRequest;
import com.hexaware.custodian_service.dto.request.SimulatedSettlementFailureRequest;
import com.hexaware.custodian_service.entity.Collateral;
import com.hexaware.custodian_service.entity.SettlementInstruction;
import com.hexaware.custodian_service.dto.response.CollateralSettlementResponse;
import com.hexaware.custodian_service.service.settlement.InitialSettlementService;
import com.hexaware.custodian_service.service.collateral.FinalCollateralReleaseService;
import com.hexaware.custodian_service.service.returns.SecuritiesReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts/{transactionReference}/settlement")
@RequiredArgsConstructor
public class InitialSettlementController {
  private final InitialSettlementService service;
  private final SecuritiesReturnService returns;
  private final FinalCollateralReleaseService releases;

  @GetMapping
  public Map<String, Object> progress(
      Authentication authentication, @PathVariable String transactionReference) {
    return service.progress(authentication, transactionReference);
  }

  @PostMapping("/cash-instructions")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public CollateralSettlementResponse submitCash(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody CashCollateralInstructionRequest request) {
    Collateral collateral = service.submitCash(authentication, transactionReference, request);
    return new CollateralSettlementResponse(collateral.getCollateralReference(), null,
        collateral.getStatus(), collateral.getCurrency(), null,
        collateral.getPostedCashAmount(), false);
  }

  @PostMapping("/cash-instructions/simulated-confirm")
  public CollateralSettlementResponse confirmCash(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody SimulatedSettlementConfirmationRequest request) {
    SettlementInstruction instruction =
        service.confirmCash(authentication, transactionReference, request);
    return new CollateralSettlementResponse(instruction.getInstructionReference(),
        null, "POSTED", instruction.getCurrency(), instruction.getStatus().name(),
        instruction.getAmount(), true);
  }

  @PostMapping("/securities/simulated-confirm")
  public CollateralSettlementResponse confirmSecurities(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody SimulatedSettlementConfirmationRequest request) {
    SettlementInstruction instruction =
        service.confirmSecurities(authentication, transactionReference, request);
    return new CollateralSettlementResponse(instruction.getInstructionReference(),
        null, null, instruction.getCurrency(), instruction.getStatus().name(),
        instruction.getAmount(), true);
  }

  @PostMapping("/returns/simulated-confirm")
  public CollateralSettlementResponse confirmReturn(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody SimulatedSettlementConfirmationRequest request) {
    SettlementInstruction instruction =
        returns.confirmReturn(authentication, transactionReference, request);
    return new CollateralSettlementResponse(instruction.getInstructionReference(),
        instruction.getSecurityIsin(), null, instruction.getCurrency(),
        instruction.getStatus().name(), null, true);
  }

  @PostMapping("/returns/simulated-fail")
  public CollateralSettlementResponse failReturn(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody SimulatedSettlementFailureRequest request) {
    SettlementInstruction instruction =
        returns.failReturn(authentication, transactionReference, request);
    return new CollateralSettlementResponse(instruction.getInstructionReference(),
        instruction.getSecurityIsin(), null, instruction.getCurrency(),
        instruction.getStatus().name(), null, false);
  }

  @PostMapping("/collateral-releases/simulated-confirm")
  public CollateralSettlementResponse confirmCollateralRelease(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody SimulatedSettlementConfirmationRequest request) {
    SettlementInstruction instruction = releases.confirm(
        authentication, transactionReference, request.instructionReference(),
        request.confirmationReference());
    return new CollateralSettlementResponse(instruction.getInstructionReference(), null,
        "RELEASED", instruction.getCurrency(), instruction.getStatus().name(),
        instruction.getAmount(), true);
  }

  @PostMapping("/collateral-releases/simulated-fail")
  public CollateralSettlementResponse failCollateralRelease(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody SimulatedSettlementFailureRequest request) {
    SettlementInstruction instruction = releases.fail(
        authentication, transactionReference, request.instructionReference(),
        request.failureReason());
    return new CollateralSettlementResponse(instruction.getInstructionReference(), null,
        null, instruction.getCurrency(), instruction.getStatus().name(),
        instruction.getAmount(), false);
  }
}
