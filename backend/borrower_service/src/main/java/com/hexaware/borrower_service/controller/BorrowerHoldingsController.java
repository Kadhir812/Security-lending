package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.response.BorrowerHoldingsResponse;
import com.hexaware.borrower_service.service.holdings.BorrowerHoldingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/holdings")
public class BorrowerHoldingsController {
  private final BorrowerHoldingsService holdings;

  @GetMapping
  public BorrowerHoldingsResponse holdings(Authentication authentication) {
    return holdings.holdings(authentication);
  }
}
