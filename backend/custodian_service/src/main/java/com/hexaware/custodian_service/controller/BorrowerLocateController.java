package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.LocateSubmissionRequest;
import com.hexaware.custodian_service.dto.response.LocateResponse;
import com.hexaware.custodian_service.service.lending.LocateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/borrower/locates")
public class BorrowerLocateController {
  private final LocateService service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LocateResponse submit(
      Authentication authentication, @Valid @RequestBody LocateSubmissionRequest request) {
    return service.submit(authentication, request);
  }

  @GetMapping("/{reference}")
  public LocateResponse get(Authentication authentication, @PathVariable String reference) {
    return service.getForBorrower(authentication, reference);
  }
}
