package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.CashCollateralInstructionRequest;
import com.hexaware.borrower_service.service.collateral.CollateralInstructionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts/{transactionReference}/margin-calls/{marginCallReference}")
public class MarginCallController {
  private final CollateralInstructionService service;

  @GetMapping
  public Map<String, Object> get(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String marginCallReference) {
    return service.callProgress(authentication, transactionReference, marginCallReference);
  }

  @GetMapping("/top-ups")
  public List<Map<String, Object>> listTopUps(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String marginCallReference) {
    return service.listTopUps(authentication, transactionReference, marginCallReference);
  }

  @PostMapping("/top-ups")
  public Map<String, Object> submitTopUp(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String marginCallReference,
      @Valid @RequestBody CashCollateralInstructionRequest request) {
    return service.submitTopUp(authentication, transactionReference, marginCallReference, request);
  }
}
