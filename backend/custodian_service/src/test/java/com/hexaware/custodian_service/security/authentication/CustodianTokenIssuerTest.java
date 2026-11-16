package com.hexaware.custodian_service.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.User;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CustodianTokenIssuerTest {

  @Test
  void issues24HourCustodianToken() throws Exception {
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var tokenIssuer = issuer(Clock.fixed(now, ZoneOffset.UTC));
    var organization = Organization.builder().organizationReference("CUSTODIAN-1").build();
    var user =
        User.builder()
            .identitySubject("custodian-subject")
            .organization(organization)
            .build();
    var claims = SignedJWT.parse(tokenIssuer.issue(user)).getJWTClaimsSet();

    assertThat(claims.getAudience()).containsExactly("custodian-service");
    assertThat(claims.getSubject()).isEqualTo("custodian-subject");
    assertThat(claims.getStringClaim("organization_reference")).isEqualTo("CUSTODIAN-1");
    assertThat(claims.getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(86_400));
    assertThat(tokenIssuer.lifetimeSeconds()).isEqualTo(86_400);
  }

  private CustodianTokenIssuer issuer(Clock clock) {
    String secret =
        Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    return new CustodianTokenIssuer(
        "securities-lending", "custodian-service", secret, clock);
  }
}
