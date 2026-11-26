package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.LenderCashMovementRequest;
import com.hexaware.custodian_service.service.cash.LenderCashAccountService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lenders/{lenderReference}/cash-accounts/{currency}")
public class LenderCashAccountController {
  private final LenderCashAccountService cash;

  @GetMapping
  public Map<String, Object> account(Authentication authentication,
      @PathVariable String lenderReference, @PathVariable String currency) {
    return cash.account(authentication, lenderReference, currency);
  }

  @GetMapping("/transactions")
  public List<Map<String, Object>> history(Authentication authentication,
      @PathVariable String lenderReference, @PathVariable String currency) {
    return cash.history(authentication, lenderReference, currency);
  }

  @PostMapping("/deposits")
  public Map<String, Object> deposit(Authentication authentication,
      @PathVariable String lenderReference, @PathVariable String currency,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody LenderCashMovementRequest request) {
    return cash.move(authentication, lenderReference, currency, idempotencyKey, request, true);
  }

  @PostMapping("/withdrawals")
  public Map<String, Object> withdraw(Authentication authentication,
      @PathVariable String lenderReference, @PathVariable String currency,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody LenderCashMovementRequest request) {
    return cash.move(authentication, lenderReference, currency, idempotencyKey, request, false);
  }
}
