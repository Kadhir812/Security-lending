package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.service.fees.LendingFeeAccrualService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts/{transactionReference}")
public class LendingFeeAccrualController {
  private final LendingFeeAccrualService accruals;

  @PostMapping("/accruals")
  public Map<String, Object> accrue(
      Authentication authentication,
      @PathVariable String transactionReference,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestHeader(name = "Idempotency-Key", required = false) String correctionReference) {
    return accruals.accrue(
        authentication, transactionReference, from, to, correctionReference);
  }

  @GetMapping("/charges")
  public List<Map<String, Object>> charges(
      Authentication authentication, @PathVariable String transactionReference) {
    return accruals.charges(authentication, transactionReference);
  }
}
