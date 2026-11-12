package com.hexaware.custodian_service.controller;

import com.hexaware.custodian_service.dto.request.DemoSignupRequest;
import com.hexaware.custodian_service.dto.request.LoginRequest;
import com.hexaware.custodian_service.dto.response.AuthResponse;
import com.hexaware.custodian_service.security.authentication.LocalUserAuthentication;
import com.hexaware.custodian_service.service.authentication.DemoAuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthenticationController {

  private final DemoAuthenticationService authentication;

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse signup(@Valid @RequestBody DemoSignupRequest request) {
    return authentication.signup(request);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authentication.login(request);
  }

  @GetMapping("/me")
  public AuthResponse.UserView current(@AuthenticationPrincipal LocalUserAuthentication user) {
    return authentication.current(user);
  }
}
