package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.SimulatedFeePaymentConfirmationRequest;
import com.hexaware.custodian_service.dto.request.SimulatedFeePaymentFailureRequest;
import com.hexaware.custodian_service.service.fees.LendingFeePaymentService;
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
@RequestMapping("/api/contracts/{transactionReference}/payments")
public class LendingFeePaymentController {
  private final LendingFeePaymentService payments;

  @GetMapping
  public List<Map<String, Object>> list(
      Authentication authentication, @PathVariable String transactionReference) {
    return payments.list(authentication, transactionReference);
  }

  @PostMapping("/{paymentReference}/confirm")
  public Map<String, Object> confirm(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String paymentReference,
      @Valid @RequestBody SimulatedFeePaymentConfirmationRequest request) {
    return payments.confirm(authentication, transactionReference, paymentReference, request);
  }

  @PostMapping("/{paymentReference}/fail")
  public Map<String, Object> fail(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String paymentReference,
      @Valid @RequestBody SimulatedFeePaymentFailureRequest request) {
    return payments.fail(authentication, transactionReference, paymentReference, request);
  }
}
