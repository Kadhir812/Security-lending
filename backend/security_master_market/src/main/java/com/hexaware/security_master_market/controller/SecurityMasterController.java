package com.hexaware.security_master_market.controller;

import com.hexaware.security_master_market.dto.request.CreateSecurityRequest;
import com.hexaware.security_master_market.dto.request.UpdateSecurityRequest;
import com.hexaware.security_master_market.dto.request.UpdateSecurityStatusRequest;
import com.hexaware.security_master_market.dto.response.PageResponse;
import com.hexaware.security_master_market.dto.response.SecurityResponse;
import com.hexaware.security_master_market.enums.SecurityStatus;
import com.hexaware.security_master_market.enums.SecurityType;
import com.hexaware.security_master_market.service.SecurityMasterService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/securities")
@RequiredArgsConstructor
public class SecurityMasterController {
  private final SecurityMasterService securities;

  @PostMapping
  public ResponseEntity<SecurityResponse> register(
      @Valid @RequestBody CreateSecurityRequest request) {
    SecurityResponse result = securities.register(request);
    return ResponseEntity.created(URI.create("/api/securities/" + result.getIsin())).body(result);
  }

  @GetMapping
  public PageResponse<SecurityResponse> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) SecurityType securityType,
      @RequestParam(required = false) SecurityStatus status,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String exchange,
      @RequestParam(defaultValue = "false") boolean activeOnly,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return securities.search(q, securityType, status, currency, exchange, activeOnly, page, size);
  }

  @GetMapping("/{isin}")
  public SecurityResponse details(@PathVariable String isin) {
    return securities.details(isin);
  }

  @PutMapping("/{isin}")
  public SecurityResponse update(
      @PathVariable String isin, @Valid @RequestBody UpdateSecurityRequest request) {
    return securities.update(isin, request);
  }

  @PatchMapping("/{isin}/status")
  public SecurityResponse status(
      @PathVariable String isin, @Valid @RequestBody UpdateSecurityStatusRequest request) {
    return securities.status(isin, request);
  }
}
