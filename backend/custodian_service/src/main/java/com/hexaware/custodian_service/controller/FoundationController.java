package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.response.FoundationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/foundation")
public class FoundationController {
  @GetMapping("/status")
  public FoundationStatus status() {
    return FoundationStatus.builder().service("custodian-service").stage(1).build();
  }
}
