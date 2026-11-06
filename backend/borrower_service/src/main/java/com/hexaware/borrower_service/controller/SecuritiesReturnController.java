package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.SecuritiesReturnRequest;
import com.hexaware.borrower_service.service.collateral.CollateralInstructionService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/contracts/{transactionReference}/returns")
public class SecuritiesReturnController {
  private final CollateralInstructionService service;

  @GetMapping
  public Map<String, Object> progress(
      Authentication authentication, @PathVariable String transactionReference) {
    return service.returnProgress(authentication, transactionReference);
  }

  @PostMapping
  public Map<String, Object> submit(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody SecuritiesReturnRequest request) {
    return service.submitReturn(authentication, transactionReference, request);
  }
}
