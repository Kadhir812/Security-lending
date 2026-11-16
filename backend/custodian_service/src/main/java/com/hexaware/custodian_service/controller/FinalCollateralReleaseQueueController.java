package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.service.collateral.FinalCollateralReleaseService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/collateral-releases/requests")
public class FinalCollateralReleaseQueueController {
  private final FinalCollateralReleaseService releases;

  @GetMapping
  public List<Map<String, Object>> pendingRequests(Authentication authentication) {
    return releases.pendingRequests(authentication);
  }
}
