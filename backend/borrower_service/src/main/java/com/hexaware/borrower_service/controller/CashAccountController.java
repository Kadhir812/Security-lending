package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.CashMovementRequest;
import com.hexaware.borrower_service.dto.request.TradeExecutionRequest;
import com.hexaware.borrower_service.service.cash.BorrowerCashAccountService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cash-accounts")
public class CashAccountController {
  private final BorrowerCashAccountService cashAccounts;

  @GetMapping("/{currency}")
  public Map<String, Object> account(
      Authentication authentication, @PathVariable String currency) {
    return cashAccounts.account(authentication, currency);
  }

  @GetMapping("/{currency}/transactions")
  public List<Map<String, Object>> transactions(
      Authentication authentication, @PathVariable String currency) {
    return cashAccounts.transactionHistory(authentication, currency);
  }

  @PostMapping("/{currency}/deposits")
  public Map<String, Object> deposit(
      Authentication authentication,
      @PathVariable String currency,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CashMovementRequest request) {
    return cashAccounts.deposit(authentication, currency, idempotencyKey, request);
  }

  @PostMapping("/{currency}/withdrawals")
  public Map<String, Object> withdraw(
      Authentication authentication,
      @PathVariable String currency,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CashMovementRequest request) {
    return cashAccounts.withdraw(authentication, currency, idempotencyKey, request);
  }

  @PostMapping("/trades")
  public Map<String, Object> trade(
      Authentication authentication, @Valid @RequestBody TradeExecutionRequest request) {
    return cashAccounts.executeTrade(authentication, request);
  }

  @GetMapping("/trades/{transactionReference}")
  public List<Map<String, Object>> tradeHistory(
      Authentication authentication, @PathVariable String transactionReference) {
    return cashAccounts.tradeHistory(authentication, transactionReference);
  }
}
