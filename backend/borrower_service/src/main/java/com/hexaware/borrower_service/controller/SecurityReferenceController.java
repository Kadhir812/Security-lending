package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.client.BorrowerSecurityReference;
import com.hexaware.borrower_service.client.SecurityReferenceClient;
import java.util.List;
import com.hexaware.borrower_service.client.BorrowerSecurityPrice;
import com.hexaware.borrower_service.client.BorrowerSecurityPricePage;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reference/securities")
public class SecurityReferenceController {
  private final SecurityReferenceClient securities;

  @GetMapping
  public List<BorrowerSecurityReference> listActive(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Authenticated borrower required");
    }
    return securities.listActive();
  }

  @GetMapping("/{isin}/prices/latest")
  public BorrowerSecurityPrice latest(
      Authentication authentication, @PathVariable String isin) {
    requireBorrower(authentication);
    return securities.latestPrice(isin);
  }

  @GetMapping("/{isin}/prices")
  public BorrowerSecurityPricePage history(
      Authentication authentication,
      @PathVariable String isin,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "200") int size) {
    requireBorrower(authentication);
    if (page < 0 || size < 1 || size > 100)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid price history page");
    return securities.priceHistory(isin, from, to, page, size);
  }

  private void requireBorrower(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated())
      throw new org.springframework.security.access.AccessDeniedException(
          "Authenticated borrower required");
  }
}
