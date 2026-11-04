package com.hexaware.valuation_accural.controller;

import com.hexaware.valuation_accural.dto.response.FoundationStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/foundation")
public class FoundationController {
  @GetMapping("/status")
  public FoundationStatus status() {
    return FoundationStatus.builder().service("valuation-service").stage(1).build();
  }
}
