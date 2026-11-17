package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.FeePaymentRequest;
import com.hexaware.borrower_service.service.fees.FeePaymentService;
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
@RequestMapping("/api/contracts/{transactionReference}")
public class FeePaymentController {
  private final FeePaymentService payments;

  @GetMapping("/charges")
  public List<Map<String, Object>> charges(
      Authentication authentication, @PathVariable String transactionReference) {
    return payments.charges(authentication, transactionReference);
  }

  @GetMapping("/payments")
  public List<Map<String, Object>> list(
      Authentication authentication, @PathVariable String transactionReference) {
    return payments.list(authentication, transactionReference);
  }

  @PostMapping("/payments")
  public Map<String, Object> submit(
      Authentication authentication,
      @PathVariable String transactionReference,
      @RequestHeader("Idempotency-Key") String paymentReference,
      @Valid @RequestBody FeePaymentRequest request) {
    return payments.submit(authentication, transactionReference, paymentReference, request);
  }
}
