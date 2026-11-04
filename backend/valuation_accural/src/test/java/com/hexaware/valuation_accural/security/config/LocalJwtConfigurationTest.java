package com.hexaware.valuation_accural.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(JwtConfiguration.class)
@TestPropertySource(
    properties = {
      "app.auth.issuer=securities-lending",
      "app.auth.audience=valuation-service",
      "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
    })
class JwtConfigurationTest {
  private static final String SECRET =
      "0123456789abcdef0123456789abcdef";

  @Autowired JwtDecoder decoder;

  @Test
  void validatesLocallySignedJwtClaimsAndRejectsWrongSignature() {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("securities-lending")
            .subject("local-user")
            .audience(List.of("valuation-service"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(60))
            .claim("organization_reference", "LOCAL-CUSTODIAN")
            .build();
    String validToken = token(SECRET, claims);

    assertThat(decoder.decode(validToken).getSubject()).isEqualTo("local-user");
    assertThatThrownBy(() -> decoder.decode(token("other-secret-other-secret-123456", claims)))
        .isInstanceOf(RuntimeException.class);
  }

  private String token(String secret, JwtClaimsSet claims) {
    var key =
        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
