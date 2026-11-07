package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.LendingFeeRequest;
import com.hexaware.custodian_service.dto.response.LendingFeeResponse;
import com.hexaware.custodian_service.service.fees.LendingFeeService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mslas/{mslaReference}/lending-fees")
@RequiredArgsConstructor
public class LendingFeeController {

  private final LendingFeeService service;

  @PostMapping
  public LendingFeeResponse create(
      Authentication authentication,
      @PathVariable String mslaReference,
      @Valid @RequestBody LendingFeeRequest request) {
    return service.create(authentication, mslaReference, request);
  }

  @GetMapping
  public List<LendingFeeResponse> list(
      Authentication authentication, @PathVariable String mslaReference) {
    return service.list(authentication, mslaReference);
  }

  @GetMapping("/{feeId}")
  public LendingFeeResponse get(
      Authentication authentication, @PathVariable String mslaReference, @PathVariable Long feeId) {
    return service.get(authentication, mslaReference, feeId);
  }

  @PutMapping("/{feeId}")
  public LendingFeeResponse update(
      Authentication authentication,
      @PathVariable String mslaReference,
      @PathVariable Long feeId,
      @Valid @RequestBody LendingFeeRequest request) {
    return service.update(authentication, mslaReference, feeId, request);
  }

  @GetMapping("/effective")
  public LendingFeeResponse effective(
      Authentication authentication,
      @PathVariable String mslaReference,
      @RequestParam String securityIsin,
      @RequestParam LocalDate businessDate) {
    return service.selectEffective(authentication, mslaReference, securityIsin, businessDate);
  }
}
