package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.CalculationScheduleRequest;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.fees.CalculationScheduleService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/calculations/schedule")
public class CalculationScheduleController {
  private final SetupAccess access;
  private final CalculationScheduleService scheduler;

  @GetMapping
  public Map<String, Object> get(Authentication authentication) {
    access.actor(authentication, false);
    return scheduler.schedule();
  }

  @PutMapping
  public Map<String, Object> update(
      Authentication authentication, @Valid @RequestBody CalculationScheduleRequest request) {
    access.actor(authentication, false);
    return scheduler.updateInterval(request.intervalMinutes());
  }
}
