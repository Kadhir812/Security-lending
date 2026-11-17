package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.CreateRebatePaymentRequest;
import com.hexaware.custodian_service.dto.request.RebatePaymentConfirmationRequest;
import com.hexaware.custodian_service.dto.request.RebatePaymentFailureRequest;
import com.hexaware.custodian_service.service.fees.CashRebatePaymentService;
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
@RequestMapping("/api/contracts/{transactionReference}/rebates/payments")
public class CashRebatePaymentController {
  private final CashRebatePaymentService payments;

  @GetMapping
  public List<Map<String, Object>> list(
      Authentication authentication, @PathVariable String transactionReference) {
    return payments.list(authentication, transactionReference);
  }

  @PostMapping
  public Map<String, Object> create(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody CreateRebatePaymentRequest request) {
    return payments.create(authentication, transactionReference, request);
  }

  @PostMapping("/{paymentReference}/confirm")
  public Map<String, Object> confirm(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String paymentReference,
      @Valid @RequestBody RebatePaymentConfirmationRequest request) {
    return payments.confirm(authentication, transactionReference, paymentReference, request);
  }

  @PostMapping("/{paymentReference}/fail")
  public Map<String, Object> fail(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String paymentReference,
      @Valid @RequestBody RebatePaymentFailureRequest request) {
    return payments.fail(authentication, transactionReference, paymentReference, request);
  }
}
