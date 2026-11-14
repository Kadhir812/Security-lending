package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.CreateRecallRequest;
import com.hexaware.custodian_service.service.returns.SecuritiesReturnService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/contracts/{transactionReference}/recalls")
public class RecallController {
  private final SecuritiesReturnService service;

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, Object> create(
      Authentication authentication,
      @PathVariable String transactionReference,
      @Valid @RequestBody CreateRecallRequest request) {
    return service.createRecall(authentication, transactionReference, request);
  }

  @GetMapping
  public List<Map<String, Object>> list(
      Authentication authentication, @PathVariable String transactionReference) {
    return service.listRecalls(authentication, transactionReference);
  }

  @GetMapping("/{recallReference}")
  public Map<String, Object> get(
      Authentication authentication,
      @PathVariable String transactionReference,
      @PathVariable String recallReference) {
    return service.getRecall(authentication, transactionReference, recallReference);
  }
}
