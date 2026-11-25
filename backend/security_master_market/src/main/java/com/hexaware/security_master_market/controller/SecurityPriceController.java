package com.hexaware.security_master_market.controller;

import com.hexaware.security_master_market.dto.request.SubmitPriceRequest;
import com.hexaware.security_master_market.dto.response.PageResponse;
import com.hexaware.security_master_market.dto.response.PriceResponse;
import com.hexaware.security_master_market.service.SecurityPriceService;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/securities/{isin}/prices")
@RequiredArgsConstructor
public class SecurityPriceController {
  private final SecurityPriceService prices;

  @PostMapping
  public PriceResponse submit(
      @PathVariable String isin, @Valid @RequestBody SubmitPriceRequest request) {
    return prices.submit(isin, request);
  }

  @GetMapping("/latest")
  public PriceResponse latest(
      @PathVariable String isin, @RequestParam(required = false) String source) {
    return prices.latest(isin, source);
  }

  @GetMapping("/as-of")
  public PriceResponse asOf(
      @PathVariable String isin,
      @RequestParam Instant at,
      @RequestParam(required = false) String source) {
    return prices.asOf(isin, at, source);
  }

  @GetMapping
  public PageResponse<PriceResponse> history(
      @PathVariable String isin,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String source,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return prices.history(isin, from, to, source, page, size);
  }
}
