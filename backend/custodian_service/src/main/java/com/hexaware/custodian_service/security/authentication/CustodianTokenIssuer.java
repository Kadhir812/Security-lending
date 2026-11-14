package com.hexaware.custodian_service.security.authentication;

import com.hexaware.custodian_service.entity.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class CustodianTokenIssuer {

  private static final Duration TOKEN_LIFETIME = Duration.ofDays(1);
  private final String issuer;
  private final String audience;
  private final NimbusJwtEncoder encoder;
  private final Clock clock;

  public CustodianTokenIssuer(
      @Value("${app.auth.issuer}") String issuer,
      @Value("${app.auth.audience}") String audience,
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

  public String issuer() {
    return issuer;
  }

  public String issue(User user) {
    var now = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.getIdentitySubject())
            .audience(List.of(audience))
            .issuedAt(now)
            .expiresAt(now.plus(TOKEN_LIFETIME))
            .claim("organization_reference", user.getOrganization().getOrganizationReference())
            .build();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  public long lifetimeSeconds() {
    return TOKEN_LIFETIME.toSeconds();
  }
}
