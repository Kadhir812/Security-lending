package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.response.CollateralInquiryResponse;
import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.service.collateral.CollateralInquiryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collateral-inquiries")
@RequiredArgsConstructor
public class CollateralInquiryController {
  private final CollateralInquiryQueryService inquiries;

  @GetMapping
  public PageResponse<CollateralInquiryResponse> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return inquiries.list(authentication, page, size);
  }
}
