package com.hexaware.valuation_accural.controller;

import com.hexaware.valuation_accural.dto.request.LoanApprovalCalculationRequest;
import com.hexaware.valuation_accural.dto.request.MtmCalculationRequest;
import com.hexaware.valuation_accural.dto.request.LendingFeeCalculationRequest;
import com.hexaware.valuation_accural.dto.response.LoanApprovalCalculationResponse;
import com.hexaware.valuation_accural.dto.response.LendingFeeCalculationResponse;
import com.hexaware.valuation_accural.dto.response.MtmCalculationResponse;
import com.hexaware.valuation_accural.dto.request.RebateCalculationRequest;
import com.hexaware.valuation_accural.dto.response.RebateCalculationResponse;
import com.hexaware.valuation_accural.service.LendingFeeCalculationService;
import com.hexaware.valuation_accural.service.LoanApprovalCalculationService;
import com.hexaware.valuation_accural.service.MtmCalculationService;
import com.hexaware.valuation_accural.service.RebateCalculationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calculations")
@RequiredArgsConstructor
public class LoanApprovalCalculationController {
  private final LoanApprovalCalculationService calculations;
  private final MtmCalculationService mtmCalculations;
  private final LendingFeeCalculationService lendingFees;
  private final RebateCalculationService rebates;

  @PostMapping("/loan-approval")
  public LoanApprovalCalculationResponse calculate(
      Authentication authentication,
      @Valid @RequestBody LoanApprovalCalculationRequest request) {
    return calculations.calculate(authentication, request);
  }

  @PostMapping("/mtm")
  public MtmCalculationResponse mtm(
      Authentication authentication,
      @Valid @RequestBody MtmCalculationRequest request) {
    return mtmCalculations.calculate(authentication, request);
  }

  @PostMapping("/lending-fee")
  public LendingFeeCalculationResponse calculateLendingFee(
      Authentication authentication,
      @Valid @RequestBody LendingFeeCalculationRequest request) {
    return lendingFees.calculate(authentication, request);
  }

  @GetMapping("/lending-fee")
  public List<LendingFeeCalculationResponse> lendingFeeResults(
      Authentication authentication,
      @RequestParam String transactionReference,
      @RequestParam String custodianOrganizationReference,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    return lendingFees.list(
        authentication, transactionReference, custodianOrganizationReference, from, to);
  }

  @PostMapping("/rebate")
  public RebateCalculationResponse calculateRebate(
      Authentication authentication, @Valid @RequestBody RebateCalculationRequest request) {
    return rebates.calculate(authentication, request);
  }

  @GetMapping("/rebate")
  public List<RebateCalculationResponse> rebateResults(
      Authentication authentication,
      @RequestParam String transactionReference,
      @RequestParam String custodianOrganizationReference,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to) {
    return rebates.list(authentication, transactionReference, custodianOrganizationReference, from, to);
  }
}
