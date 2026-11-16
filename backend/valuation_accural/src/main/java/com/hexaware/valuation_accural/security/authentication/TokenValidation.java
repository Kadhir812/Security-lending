package com.hexaware.valuation_accural.security.authentication;

import java.time.Duration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/** Only called after cryptographic signature verification by NimbusJwtDecoder. */
public final class TokenValidation {
  private TokenValidation() {}

  public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
    if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
      throw new IllegalArgumentException("Trusted issuer and audience are required");
    }
    return new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(Duration.ZERO),
        new JwtIssuerValidator(issuer),
        token -> {
          Object subject = token.getClaims().get("sub");
          Object org = token.getClaims().get("organization_reference");
          boolean valid =
              token.getExpiresAt() != null
                  && token.getAudience() != null
                  && token.getAudience().contains(audience)
                  && subject instanceof String s
                  && !s.isBlank()
                  && s.length() <= 255
                  && org instanceof String o
                  && o.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}");
          return valid
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error(
                      "invalid_token", "Required audience or identity claims are invalid", null));
        });
  }
}
