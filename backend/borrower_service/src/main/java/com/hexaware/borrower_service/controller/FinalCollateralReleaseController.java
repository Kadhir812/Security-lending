package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.FinalCollateralReleaseRequest;
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
@RequestMapping("/api/contracts/{transactionReference}/collateral-releases")
public class FinalCollateralReleaseController {
  private final CollateralInstructionService service;

  @GetMapping
  public List<Map<String, Object>> list(
      Authentication authentication, @PathVariable String transactionReference) {
    return service.finalReleaseRequests(authentication, transactionReference);
  }

  @PostMapping
  public Map<String, Object> submit(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody FinalCollateralReleaseRequest request) {
    return service.submitFinalRelease(authentication, transactionReference, request);
  }
}
