package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.LocateSubmission;
import com.hexaware.borrower_service.dto.response.LocateResponse;
import com.hexaware.borrower_service.service.loan.BorrowerLocateService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/locates")
public class LocateController {
  private final BorrowerLocateService service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LocateResponse submit(
      Authentication authentication, @Valid @RequestBody LocateSubmission submission) {
    return service.submit(authentication, submission);
  }

  @GetMapping
  public List<LocateResponse> list(Authentication authentication) {
    return service.list(authentication);
  }

  @GetMapping("/{reference}")
  public LocateResponse get(Authentication authentication, @PathVariable String reference) {
    return service.get(authentication, reference);
  }
}
