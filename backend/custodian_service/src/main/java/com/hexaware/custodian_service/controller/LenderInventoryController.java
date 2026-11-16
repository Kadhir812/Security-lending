package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.InventoryCreateRequest;
import com.hexaware.custodian_service.dto.request.InventoryUpdateRequest;
import com.hexaware.custodian_service.dto.response.InventoryResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.service.inventory.LenderInventoryService;
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
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class LenderInventoryController {

  private final LenderInventoryService service;

  @PostMapping
  public InventoryResponse create(
      Authentication authentication, @Valid @RequestBody InventoryCreateRequest request) {
    return service.create(authentication, request);
  }

  @GetMapping("/{lenderReference}/{securityIsin}")
  public InventoryResponse get(
      Authentication authentication,
      @PathVariable String lenderReference,
      @PathVariable String securityIsin) {
    return service.get(authentication, lenderReference, securityIsin);
  }

  @GetMapping
  public PageResponse<InventoryResponse> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(authentication, page, size);
  }

  @PutMapping("/{lenderReference}/{securityIsin}")
  public InventoryResponse update(
      Authentication authentication,
      @PathVariable String lenderReference,
      @PathVariable String securityIsin,
      @Valid @RequestBody InventoryUpdateRequest request) {
    return service.update(authentication, lenderReference, securityIsin, request);
  }
}
