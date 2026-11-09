package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.service.collateral.FinalCollateralReleaseService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contracts/{transactionReference}/collateral-releases")
public class FinalCollateralReleaseController {
  private final FinalCollateralReleaseService releases;

  @PostMapping("/{releaseReference}/approve")
  public Map<String, Object> approve(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String releaseReference) {
    return releases.approve(authentication, transactionReference, releaseReference);
  }
}
