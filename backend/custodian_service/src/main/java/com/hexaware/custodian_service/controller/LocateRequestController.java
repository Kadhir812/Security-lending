package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.LocateApprovalRequest;
import com.hexaware.custodian_service.dto.request.LocateRejectionRequest;
import com.hexaware.custodian_service.dto.response.CustodianLocateRequestResponse;
import com.hexaware.custodian_service.service.lending.LocateDecisionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/locate-requests")
public class LocateRequestController {
  private final LocateDecisionService locates;

  @GetMapping
  public List<Map<String, Object>> pending(Authentication authentication) {
    return locates.pending(authentication);
  }

  @PostMapping("/{locateReference}/approve")
  public Map<String, Object> approve(
      Authentication authentication,
      @PathVariable String locateReference,
      @Valid @RequestBody LocateApprovalRequest request) {
    return locates.approve(authentication, locateReference, request.approvedQuantity());
  }

  @PostMapping("/{locateReference}/reject")
  public Map<String, Object> reject(
      Authentication authentication,
      @PathVariable String locateReference,
      @Valid @RequestBody LocateRejectionRequest request) {
    return locates.reject(authentication, locateReference, request.reason());
  }
}
