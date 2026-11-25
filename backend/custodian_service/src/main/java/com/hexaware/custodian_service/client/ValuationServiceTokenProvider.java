package com.hexaware.custodian_service.client;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import java.time.Clock;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class ValuationServiceTokenProvider {
  private final String issuer;
  private final String audience;
  private final NimbusJwtEncoder encoder;
  private final Clock clock;

  public ValuationServiceTokenProvider(
      @Value("${app.auth.issuer}") String issuer,
      @Value("${app.valuation.audience:valuation-service}") String audience,
      @Value("${app.auth.jwt-secret}") String encodedSecret,
      Clock clock) {
    byte[] secret = Base64.getDecoder().decode(encodedSecret);
    if (secret.length < 32) {
      throw new IllegalArgumentException("JWT_SHARED_SECRET must decode to at least 32 bytes");
    }
    this.issuer = issuer;
    this.audience = audience;
    this.encoder =
        new NimbusJwtEncoder(
            new ImmutableSecret<>(new SecretKeySpec(secret, "HmacSHA256")));
    this.clock = clock;
  }

  public String token(String custodianOrganizationReference) {
    var now = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("custodian-service")
            .audience(java.util.List.of(audience))
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(2)))
            .claim("client_id", "custodian-service")
            .claim("identity_kind", "service")
            .claim("scope", "valuation.authoritative")
            .claim("organization_reference", custodianOrganizationReference)
            .build();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
