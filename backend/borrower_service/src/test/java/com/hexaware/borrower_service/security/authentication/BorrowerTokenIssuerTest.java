package com.hexaware.borrower_service.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.borrower_service.entity.User;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class BorrowerTokenIssuerTest {

  @Test
  void issuesTokenForBorrowerAndCustodianApis() throws Exception {
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var tokenIssuer = issuer(Clock.fixed(now, ZoneOffset.UTC));
    var claims = SignedJWT.parse(tokenIssuer.issue(user())).getJWTClaimsSet();

    assertThat(claims.getAudience()).containsExactly("borrower-service", "custodian-service");
    assertThat(claims.getSubject()).isEqualTo("borrower-subject");
    assertThat(claims.getStringClaim("organization_reference")).isEqualTo("BORROWER-1");
    assertThat(claims.getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(86_400));
    assertThat(tokenIssuer.lifetimeSeconds()).isEqualTo(86_400);
  }

  @Test
  void issues24HourSinglePurposeCustodianLinkToken() throws Exception {
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var claims =
        SignedJWT.parse(issuer(Clock.fixed(now, ZoneOffset.UTC)).issueCustodianLink(user()))
            .getJWTClaimsSet();

    assertThat(claims.getAudience()).containsExactly("borrower-service", "custodian-service");
    assertThat(claims.getStringClaim("purpose")).isEqualTo("custodian-identity-link");
    assertThat(claims.getJWTID()).isNotBlank();
    assertThat(claims.getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(86_400));
    assertThat(claims.getStringClaim("email")).isEqualTo("borrower@example.test");
  }

  private BorrowerTokenIssuer issuer() {
    return issuer(Clock.systemUTC());
  }

  private BorrowerTokenIssuer issuer(Clock clock) {
    String secret =
        Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    return new BorrowerTokenIssuer(
        "securities-lending",
        List.of("borrower-service", "custodian-service"),
        secret,
        clock);
  }

  private User user() {
    return User.builder()
        .identitySubject("borrower-subject")
        .organizationReference("BORROWER-1")
        .organizationName("Borrower Organization")
        .username("borrower")
        .email("borrower@example.test")
        .build();
  }
}
