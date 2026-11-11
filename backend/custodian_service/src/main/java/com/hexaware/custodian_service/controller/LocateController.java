package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.LocateDecisionRequest;
import com.hexaware.custodian_service.dto.response.LocateResponse;
import com.hexaware.custodian_service.service.lending.LocateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/locates")
public class LocateController {
  private final LocateService service;

  @GetMapping
  public List<LocateResponse> list(Authentication authentication) {
    return service.list(authentication);
  }

  @GetMapping("/{reference}")
  public LocateResponse get(Authentication authentication, @PathVariable String reference) {
    return service.get(authentication, reference);
  }

  @PostMapping("/{reference}/approve")
  public LocateResponse approve(
      Authentication authentication,
      @PathVariable String reference,
      @Valid @RequestBody LocateDecisionRequest decision) {
    return service.approve(authentication, reference, decision);
  }

  @PostMapping("/{reference}/reject")
  @ResponseStatus(HttpStatus.OK)
  public LocateResponse reject(
      Authentication authentication,
      @PathVariable String reference,
      @RequestBody(required = false) LocateDecisionRequest decision) {
    return service.reject(authentication, reference, decision == null ? null : decision.reason());
  }
}
