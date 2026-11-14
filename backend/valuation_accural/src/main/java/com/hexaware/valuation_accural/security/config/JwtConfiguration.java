package com.hexaware.valuation_accural.security.config;

import com.hexaware.valuation_accural.security.authentication.TokenValidation;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfiguration {
  @Bean
  public JwtDecoder jwtDecoder(
      @Value("${app.auth.issuer}") String issuer,
      @Value("${app.auth.audience}") String audience,
      @Value("${app.auth.jwt-secret}") String encodedSecret) {
    byte[] secret = Base64.getDecoder().decode(encodedSecret);
    if (secret.length < 32) {
      throw new IllegalArgumentException("JWT_SHARED_SECRET must decode to at least 32 bytes");
    }
    var key = new SecretKeySpec(secret, "HmacSHA256");
    var decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(TokenValidation.validator(issuer, audience));
    return decoder;
  }
}
