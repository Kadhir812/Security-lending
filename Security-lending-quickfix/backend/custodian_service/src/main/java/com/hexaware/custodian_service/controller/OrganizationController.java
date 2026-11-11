package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.OrganizationRequest;
import com.hexaware.custodian_service.dto.response.OrganizationResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.service.organization.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

  private final OrganizationService service;

  @PostMapping
  public OrganizationResponse create(
      Authentication authentication, @Valid @RequestBody OrganizationRequest request) {
    return service.create(authentication, request);
  }

  @GetMapping("/{reference}")
  public OrganizationResponse get(
      Authentication authentication, @PathVariable String reference) {
    return service.get(authentication, reference);
  }

  @PutMapping("/{reference}")
  public OrganizationResponse update(
      Authentication authentication,
      @PathVariable String reference,
      @Valid @RequestBody OrganizationRequest request) {
    return service.update(authentication, reference, request);
  }

  @GetMapping
  public PageResponse<OrganizationResponse> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(authentication, page, size);
  }
}
