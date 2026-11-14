package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.LocateRequestSubmission;
import com.hexaware.borrower_service.dto.response.LocateRequestResponse;
import com.hexaware.borrower_service.service.LocateRequestService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/locates")
public class LocateRequestController {
  private final LocateRequestService locates;

  @PostMapping
  public ResponseEntity<LocateRequestResponse> submit(
      Authentication authentication,
      @RequestHeader("Idempotency-Key") String locateReference,
      @Valid @RequestBody LocateRequestSubmission submission) {
    LocateRequestResponse response = locates.submit(authentication, locateReference, submission);
    return ResponseEntity.created(URI.create("/api/locates/" + response.locateReference()))
        .body(response);
  }

  @GetMapping
  public List<LocateRequestResponse> list(Authentication authentication) {
    return locates.list(authentication);
  }

  @GetMapping("/{locateReference}")
  public LocateRequestResponse get(
      Authentication authentication, @PathVariable String locateReference) {
    return locates.get(authentication, locateReference);
  }
}
