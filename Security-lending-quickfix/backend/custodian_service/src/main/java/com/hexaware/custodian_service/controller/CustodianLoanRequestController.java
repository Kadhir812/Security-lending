package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.LoanRequestRejection;
import com.hexaware.custodian_service.dto.response.CustodianLoanRequestResponse;
import com.hexaware.custodian_service.dto.response.LoanRequestDecisionResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.service.lending.CustodianLoanRequestQueryService;
import com.hexaware.custodian_service.service.lending.LoanDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loan-requests")
@RequiredArgsConstructor
public class CustodianLoanRequestController {
  private final CustodianLoanRequestQueryService service;
  private final LoanDecisionService decisions;

  @GetMapping
  public PageResponse<CustodianLoanRequestResponse> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(authentication, page, size);
  }

  @GetMapping("/{requestReference}")
  public CustodianLoanRequestResponse get(
      Authentication authentication, @PathVariable String requestReference) {
    return service.get(authentication, requestReference);
  }

  @PostMapping("/{requestReference}/approve")
  public LoanRequestDecisionResponse approve(
      Authentication authentication, @PathVariable String requestReference) {
    return decisions.approve(authentication, requestReference);
  }

  @PostMapping("/{requestReference}/reject")
  public LoanRequestDecisionResponse reject(
      Authentication authentication,
      @PathVariable String requestReference,
      @Valid @RequestBody LoanRequestRejection rejection) {
    return decisions.reject(authentication, requestReference, rejection.reason());
  }
}
