package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.service.collateral.CollateralInstructionService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts/{transactionReference}/settlement")
public class SettlementProgressController {
  private final CollateralInstructionService settlements;

  @GetMapping
  public Map<String, Object> progress(
      Authentication authentication, @PathVariable String transactionReference) {
    return settlements.progress(authentication, transactionReference);
  }
}
