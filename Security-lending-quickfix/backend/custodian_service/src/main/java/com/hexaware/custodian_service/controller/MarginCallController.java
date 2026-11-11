package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.response.MarginCallResponse;
import com.hexaware.custodian_service.service.margin.MarginCallService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/contracts/{transactionReference}/margin-calls")
@RequiredArgsConstructor
public class MarginCallController {
  private final MarginCallService service;

  @PostMapping("/evaluate")
  public MarginCallResponse evaluate(
      Authentication authentication, @PathVariable String transactionReference) {
    return service.evaluate(authentication, transactionReference);
  }

  @PostMapping("/mtm")
  public MarginCallResponse mtm(
      Authentication authentication,
      @PathVariable String transactionReference,
      @RequestHeader(name = "Idempotency-Key") String idempotencyKey) {
    return service.triggerMtm(authentication, transactionReference, idempotencyKey);
  }

  @GetMapping
  public List<MarginCallResponse> list(
      Authentication authentication, @PathVariable String transactionReference) {
    return service.list(authentication, transactionReference);
  }

  @GetMapping("/{callReference}")
  public MarginCallResponse get(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String callReference) {
    return service.get(authentication, transactionReference, callReference);
  }
}
