package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.response.LoanContractViewResponse;
import com.hexaware.borrower_service.service.loan.LoanRequestQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts")
public class BorrowerContractListController {
  private final LoanRequestQueryService service;

  @GetMapping
  public List<LoanContractViewResponse> list(Authentication authentication) {
    return service.contracts(authentication);
  }
}
