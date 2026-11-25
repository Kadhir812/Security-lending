package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.client.SecurityReferenceClient;
import com.hexaware.custodian_service.client.SecurityReferenceResponse;
import com.hexaware.custodian_service.client.SecurityPricePageResponse;
import com.hexaware.custodian_service.client.SecurityPriceResponse;
import java.time.Instant;
import java.util.List;
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
  public List<SecurityReferenceResponse> listActive(Authentication authentication) {
    requireCustodian(authentication);
    return securities.listActive();
  }

  @GetMapping("/{isin}/prices/latest")
  public SecurityPriceResponse latest(
      Authentication authentication, @PathVariable String isin) {
    requireCustodian(authentication);
    return securities.latestPrice(isin);
  }

  @GetMapping("/{isin}/prices")
  public SecurityPricePageResponse history(
      Authentication authentication,
      @PathVariable String isin,
      @RequestParam Instant from,
      @RequestParam Instant to) {
    requireCustodian(authentication);
    if (from.isAfter(to)) throw new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.BAD_REQUEST, "from must not be after to");
    return securities.priceHistory(isin, from, to);
  }

  private void requireCustodian(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Authenticated Custodian required");
    }
  }
}
