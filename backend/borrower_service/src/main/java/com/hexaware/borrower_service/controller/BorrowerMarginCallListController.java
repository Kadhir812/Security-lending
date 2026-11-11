package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.service.collateral.CollateralInstructionService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts/{transactionReference}/margin-calls")
public class BorrowerMarginCallListController {
  private final CollateralInstructionService service;

  @GetMapping
  public List<Map<String, Object>> list(
      Authentication authentication, @PathVariable String transactionReference) {
    return service.marginCalls(authentication, transactionReference);
  }
}
