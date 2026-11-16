package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.CollateralHaircutRequest;
import com.hexaware.custodian_service.dto.response.CollateralHaircutResponse;
import com.hexaware.custodian_service.service.collateral.CollateralHaircutService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mslas/{mslaReference}/collateral-haircuts")
@RequiredArgsConstructor
public class CollateralHaircutController {

  private final CollateralHaircutService service;

  @PostMapping
  public CollateralHaircutResponse create(
      Authentication authentication,
      @PathVariable String mslaReference,
      @Valid @RequestBody CollateralHaircutRequest request) {
    return service.create(authentication, mslaReference, request);
  }

  @GetMapping
  public List<CollateralHaircutResponse> list(
      Authentication authentication, @PathVariable String mslaReference) {
    return service.list(authentication, mslaReference);
  }

  @GetMapping("/{haircutId}")
  public CollateralHaircutResponse get(
      Authentication authentication,
      @PathVariable String mslaReference,
      @PathVariable Long haircutId) {
    return service.get(authentication, mslaReference, haircutId);
  }

  @PutMapping("/{haircutId}")
  public CollateralHaircutResponse update(
      Authentication authentication,
      @PathVariable String mslaReference,
      @PathVariable Long haircutId,
      @Valid @RequestBody CollateralHaircutRequest request) {
    return service.update(authentication, mslaReference, haircutId, request);
  }
}
