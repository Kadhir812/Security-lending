package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.client.SecurityReferenceClient;
import com.hexaware.custodian_service.client.SecurityReferenceResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reference/securities")
public class SecurityReferenceController {
  private final SecurityReferenceClient securities;

  @GetMapping
  public List<SecurityReferenceResponse> listActive(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Authenticated Custodian required");
    }
    return securities.listActive();
  }
}
