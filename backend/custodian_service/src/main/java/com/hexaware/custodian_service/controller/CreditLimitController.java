package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.CreditLimitCreateRequest;
import com.hexaware.custodian_service.dto.request.CreditLimitUpdateRequest;
import com.hexaware.custodian_service.dto.response.CreditLimitHistoryResponse;
import com.hexaware.custodian_service.dto.response.CreditLimitResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.service.credit.CreditLimitService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/credit-limits")
@RequiredArgsConstructor
public class CreditLimitController {

  private final CreditLimitService service;

  @PostMapping("/{mslaReference}")
  public CreditLimitResponse create(
      Authentication authentication,
      @PathVariable String mslaReference,
      @Valid @RequestBody CreditLimitCreateRequest request) {
    return service.create(authentication, mslaReference, request);
  }

  @GetMapping("/{mslaReference}")
  public CreditLimitResponse get(
      Authentication authentication, @PathVariable String mslaReference) {
    return service.get(authentication, mslaReference);
  }

  @PutMapping("/{mslaReference}")
  public CreditLimitResponse update(
      Authentication authentication,
      @PathVariable String mslaReference,
      @Valid @RequestBody CreditLimitUpdateRequest request) {
    return service.update(authentication, mslaReference, request);
  }

  @GetMapping("/{mslaReference}/history")
  public List<CreditLimitHistoryResponse> history(
      Authentication authentication, @PathVariable String mslaReference) {
    return service.history(authentication, mslaReference);
  }

  @GetMapping
  public PageResponse<CreditLimitResponse> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(authentication, page, size);
  }
}
