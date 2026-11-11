package com.hexaware.security_master_market.security.config;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfiguration {
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${app.auth.issuer}") String issuer,
      @Value("${app.auth.audience}") String audience,
      @Value("${app.auth.jwt-secret}") String encodedSecret) {
    byte[] secret = Base64.getDecoder().decode(encodedSecret);
    if (secret.length < 32) {
      throw new IllegalArgumentException("JWT_SHARED_SECRET must decode to at least 32 bytes");
    }
    var key = new SecretKeySpec(secret, "HmacSHA256");
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(
        org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ZERO),
            new JwtIssuerValidator(issuer),
            (Jwt token) -> token.getAudience() != null && token.getAudience().contains(audience)
                ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required audience is missing", null))));
    return decoder;
  }
}
