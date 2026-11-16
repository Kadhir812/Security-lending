package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.CloseContractRequest;
import com.hexaware.custodian_service.service.collateral.FinalCollateralReleaseService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts/{transactionReference}/close")
public class ContractClosureController {
  private final FinalCollateralReleaseService releases;

  @PostMapping
  public Map<String, Object> close(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody CloseContractRequest request) {
    return releases.close(
        authentication, transactionReference, request.closureReference(), request.reason());
  }
}
