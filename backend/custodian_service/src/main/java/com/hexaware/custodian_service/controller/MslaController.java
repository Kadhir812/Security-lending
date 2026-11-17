package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.MslaRequest;
import com.hexaware.custodian_service.dto.response.MslaResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.service.agreement.MslaService;
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
@RequestMapping("/api/mslas")
@RequiredArgsConstructor
public class MslaController {

  private final MslaService service;

  @PostMapping
  public MslaResponse create(
      Authentication authentication, @Valid @RequestBody MslaRequest request) {
    return service.create(authentication, request);
  }

  @GetMapping("/{reference}")
  public MslaResponse get(Authentication authentication, @PathVariable String reference) {
    return service.get(authentication, reference);
  }

  @PutMapping("/{reference}")
  public MslaResponse update(
      Authentication authentication,
      @PathVariable String reference,
      @Valid @RequestBody MslaRequest request) {
    return service.update(authentication, reference, request);
  }

  @GetMapping
  public PageResponse<MslaResponse> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(authentication, page, size);
  }
}
