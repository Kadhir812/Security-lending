package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.BorrowerIdentityLinkRequest;
import com.hexaware.custodian_service.dto.response.BorrowerIdentityLinkResponse;
import com.hexaware.custodian_service.service.organization.BorrowerIdentityLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organizations/borrower-links")
public class BorrowerIdentityLinkController {

  private final BorrowerIdentityLinkService service;

  @PostMapping
  public BorrowerIdentityLinkResponse link(
      Authentication authentication,
      @Valid @RequestBody BorrowerIdentityLinkRequest request) {
    return service.link(authentication, request);
  }
}
