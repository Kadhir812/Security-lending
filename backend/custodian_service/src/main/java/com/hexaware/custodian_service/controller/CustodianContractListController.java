package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.service.lending.CustodianContractQueryService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts")
public class CustodianContractListController {
  private final CustodianContractQueryService service;

  @GetMapping
  public PageResponse<Map<String, Object>> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(authentication, page, size);
  }
}
