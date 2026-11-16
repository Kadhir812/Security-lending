package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.response.BorrowerEligibleMslaResponse;
import com.hexaware.custodian_service.service.agreement.BorrowerEligibleMslaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/borrower/mslas")
@RequiredArgsConstructor
public class BorrowerEligibleMslaController {
  private final BorrowerEligibleMslaService service;

  @GetMapping
  public List<BorrowerEligibleMslaResponse> list(Authentication authentication) {
    return service.list(authentication);
  }

  @GetMapping("/{reference}")
  public BorrowerEligibleMslaResponse get(
      Authentication authentication, @PathVariable String reference) {
    return service.get(authentication, reference);
  }
}
