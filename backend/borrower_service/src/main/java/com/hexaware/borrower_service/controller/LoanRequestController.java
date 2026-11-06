package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.LoanRequestSubmission;
import com.hexaware.borrower_service.dto.response.BorrowerEligibleMslaResponse;
import com.hexaware.borrower_service.dto.response.LoanRequestResponse;
import com.hexaware.borrower_service.dto.response.LoanContractViewResponse;
import com.hexaware.borrower_service.dto.request.CashCollateralInstructionRequest;
import com.hexaware.borrower_service.service.collateral.CollateralInstructionService;
import java.util.Map;
import com.hexaware.borrower_service.dto.response.PageResponse;
import com.hexaware.borrower_service.service.loan.LoanRequestService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loan-requests")
@RequiredArgsConstructor
public class LoanRequestController {
  private final LoanRequestService service;
  private final CollateralInstructionService collateralInstructions;

  @GetMapping("/eligible-mslas")
  public List<BorrowerEligibleMslaResponse> eligible(Authentication authentication) {
    return service.eligibleMslaList(authentication);
  }

  @PostMapping
  public ResponseEntity<LoanRequestResponse> submit(
      Authentication authentication,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody LoanRequestSubmission submission) {
    LoanRequestResponse response = service.submit(authentication, idempotencyKey, submission);
    return ResponseEntity.created(URI.create("/api/loan-requests/" + response.requestReference()))
        .body(response);
  }

  @GetMapping
  public PageResponse<LoanRequestResponse> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(authentication, page, size);
  }

  @GetMapping("/{reference}")
  public LoanRequestResponse get(
      Authentication authentication, @PathVariable String reference) {
    return service.get(authentication, reference);
  }

  @GetMapping("/{reference}/contract")
  public LoanContractViewResponse contract(
      Authentication authentication, @PathVariable String reference) {
    return service.contract(authentication, reference);
  }

  @PostMapping("/{reference}/collateral/cash-instructions")
  public Map<String, Object> submitCashCollateral(
      Authentication authentication,
      @PathVariable String reference,
      @Valid @RequestBody CashCollateralInstructionRequest request) {
    return collateralInstructions.submit(authentication, reference, request);
  }
}
