package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.DemoSignupRequest;
import com.hexaware.borrower_service.dto.request.LoginRequest;
import com.hexaware.borrower_service.dto.response.AuthResponse;
import com.hexaware.borrower_service.dto.response.CustodianLinkTokenResponse;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
import com.hexaware.borrower_service.service.authentication.DemoAuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
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
  public AuthResponse.UserView current(
      @CurrentSecurityContext(expression = "authentication") LocalUserAuthentication user) {
    return authentication.current(user);
  }

  @PostMapping("/custodian-link-token")
  public CustodianLinkTokenResponse custodianLinkToken(
      @CurrentSecurityContext(expression = "authentication") LocalUserAuthentication user) {
    return authentication.custodianLinkToken(user);
  }
}
