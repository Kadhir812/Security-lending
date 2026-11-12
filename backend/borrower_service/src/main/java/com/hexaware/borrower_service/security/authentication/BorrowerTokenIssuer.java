package com.hexaware.borrower_service.security.authentication;

import com.hexaware.borrower_service.entity.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class BorrowerTokenIssuer {

  private static final Duration TOKEN_LIFETIME = Duration.ofDays(1);
  private static final Duration LINK_TOKEN_LIFETIME = Duration.ofDays(1);
  private final String issuer;
  private final List<String> audiences;
  private final NimbusJwtEncoder encoder;
  private final Clock clock;

  public BorrowerTokenIssuer(
      @Value("${app.auth.issuer}") String issuer,
      @Value("${app.auth.token-audiences}") List<String> audiences,
      @Value("${app.auth.jwt-secret}") String encodedSecret,
      Clock clock) {
    if (audiences == null || audiences.isEmpty() || audiences.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("At least one token audience is required");
    }
    byte[] secret = Base64.getDecoder().decode(encodedSecret);
    if (secret.length < 32) {
      throw new IllegalArgumentException("JWT_SHARED_SECRET must decode to at least 32 bytes");
    }
    this.issuer = issuer;
    this.audiences = List.copyOf(audiences);
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
            .audience(audiences)
            .issuedAt(now)
            .expiresAt(now.plus(TOKEN_LIFETIME))
            .claim("organization_reference", user.getOrganizationReference())
            .build();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  public String issueCustodianLink(User user) {
    var now = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.getIdentitySubject())
            .audience(audiences)
            .issuedAt(now)
            .expiresAt(now.plus(LINK_TOKEN_LIFETIME))
            .id(UUID.randomUUID().toString())
            .claim("organization_reference", user.getOrganizationReference())
            .claim("organization_name", user.getOrganizationName())
            .claim("username", user.getUsername())
            .claim("email", user.getEmail())
            .claim("purpose", "custodian-identity-link")
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

  public long linkTokenLifetimeSeconds() {
    return LINK_TOKEN_LIFETIME.toSeconds();
  }
}
