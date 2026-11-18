package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.BorrowerCashAccountRequest;
import com.hexaware.borrower_service.dto.request.BorrowerExternalCashMovementRequest;
import com.hexaware.borrower_service.dto.response.BorrowerCashAccountResponse;
import com.hexaware.borrower_service.dto.response.BorrowerCashTransactionResponse;
import com.hexaware.borrower_service.dto.response.PageResponse;
import com.hexaware.borrower_service.service.cash.BorrowerCashService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@RequestMapping("/api/cash-accounts")
public class BorrowerCashController {
  private final BorrowerCashService cash;

  @PostMapping
  public BorrowerCashAccountResponse open(
      Authentication authentication, @Valid @RequestBody BorrowerCashAccountRequest request) {
    return cash.openAccount(authentication, request.currency());
  }

  @GetMapping
  public List<BorrowerCashAccountResponse> list(Authentication authentication) {
    return cash.listAccounts(authentication);
  }

  @GetMapping("/{currency}")
  public BorrowerCashAccountResponse get(
      Authentication authentication, @PathVariable String currency) {
    return cash.getAccount(authentication, currency);
  }

  @PostMapping("/{currency}/transactions")
  public BorrowerCashTransactionResponse recordExternalMovement(
      Authentication authentication,
      @PathVariable String currency,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody BorrowerExternalCashMovementRequest request) {
    return cash.recordExternalMovement(authentication, currency, idempotencyKey, request);
  }

  @GetMapping("/{currency}/transactions")
  public PageResponse<BorrowerCashTransactionResponse> transactions(
      Authentication authentication,
      @PathVariable String currency,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return cash.listTransactions(authentication, currency, page, size);
  }
}
