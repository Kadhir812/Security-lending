package com.hexaware.valuation_accural;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexaware.valuation_accural.controller.FoundationController;
import com.hexaware.valuation_accural.exception.ApiExceptionHandler;
import com.hexaware.valuation_accural.security.authentication.ServiceJwtConverter;
import com.hexaware.valuation_accural.security.authentication.TokenValidation;
import com.hexaware.valuation_accural.security.authorization.CalculationAuthorization;
import com.hexaware.valuation_accural.security.config.SecurityConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitConfig(SecurityHttpTest.Config.class)
@WebAppConfiguration
@TestPropertySource(
    properties = {
      "app.auth.preview-client-id=borrower-service",
      "app.auth.preview-subject=preview-subject",
      "app.auth.custodian-client-id=custodian-service",
      "app.auth.custodian-subject=custodian-subject"
    })
class SecurityHttpTest {
  static final String ISSUER = "https://identity.example.test/realms/lending";
  static final KeyPair KEY = keyPair();

  static KeyPair keyPair() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @TestConfiguration
  @EnableWebMvc
  @Import({
    SecurityConfiguration.class,
    FoundationController.class,
    ApiExceptionHandler.class,
    Checks.class,
    ServiceJwtConverter.class,
    CalculationAuthorization.class
  })
  static class Config {
    @Bean
    JwtDecoder jwtDecoder() {
      var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
      decoder.setJwtValidator(TokenValidation.validator(ISSUER, "valuation-service"));
      return decoder;
    }
  }

  @TestComponent
  @RestController
  static class Checks {
    private final CalculationAuthorization authorization;

    Checks(CalculationAuthorization authorization) {
      this.authorization = authorization;
    }

    @GetMapping("/test/preview/{organization}")
    void preview(Authentication user, @PathVariable String organization) {
      authorization.requirePreview(user, organization);
    }

    @GetMapping("/test/authoritative/{organization}")
    void authoritative(Authentication user, @PathVariable String organization) {
      authorization.requireAuthoritative(user, organization);
    }
  }

  @Autowired WebApplicationContext context;
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  String bearer(Map<String, Object> changes) throws Exception {
    return "Bearer " + token(changes, KEY);
  }

  String token(Map<String, Object> changes, KeyPair key) throws Exception {
    var claims = new HashMap<String, Object>();
    claims.put("iss", ISSUER);
    claims.put("aud", List.of("valuation-service"));
    claims.put("exp", Instant.now().plusSeconds(120).getEpochSecond());
    claims.put("nbf", Instant.now().minusSeconds(1).getEpochSecond());
    claims.put("organization_reference", "ORG-A");
    claims.put("sub", "preview-subject");
    claims.put("client_id", "borrower-service");
    claims.put("identity_kind", "service");
    claims.put("scope", "valuation.preview");
    claims.putAll(changes);
    var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), JWTClaimsSet.parse(claims));
    jwt.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate()));
    return jwt.serialize();
  }

  ResultActions request(Map<String, Object> changes) throws Exception {
    return mvc.perform(get("/api/foundation/status").header("Authorization", bearer(changes)));
  }

  @Test
  void validSignedTokenIsAccepted() throws Exception {
    request(Map.of()).andExpect(status().isOk());
  }

  @Test
  void missingMalformedAndBadSignatureAreRejected() throws Exception {
    mvc.perform(get("/api/foundation/status")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/foundation/status").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/api/foundation/status")
                .header("Authorization", "Bearer " + token(Map.of(), keyPair())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredFutureWrongIssuerAndWrongAudienceAreRejected() throws Exception {
    request(Map.of("exp", Instant.now().minusSeconds(120).getEpochSecond()))
        .andExpect(status().isUnauthorized());
    request(Map.of("nbf", Instant.now().plusSeconds(120).getEpochSecond()))
        .andExpect(status().isUnauthorized());
    request(Map.of("iss", "https://untrusted.example.test")).andExpect(status().isUnauthorized());
    request(Map.of("aud", List.of("other-service"))).andExpect(status().isUnauthorized());
  }

  @Test
  void requiredIdentityClaimsCannotBeMissing() throws Exception {
    for (String claim : List.of("sub", "exp", "aud", "organization_reference")) {
      var changes = new HashMap<String, Object>();
      changes.put(claim, null);
      request(changes).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void previewServiceCannotRequestAuthoritativeProcessingEvenWithForgedScope() throws Exception {
    mvc.perform(get("/test/preview/ORG-A").header("Authorization", bearer(Map.of())))
        .andExpect(status().isOk());
    mvc.perform(
            get("/test/authoritative/ORG-A")
                .header(
                    "Authorization",
                    bearer(Map.of("scope", "valuation.preview valuation.authoritative"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void onlyCustodianWithAuthoritativeScopeAndMatchingOrganizationIsAllowed() throws Exception {
    var custodian =
        Map.<String, Object>of(
            "sub",
            "custodian-subject",
            "client_id",
            "custodian-service",
            "scope",
            "valuation.authoritative");
    mvc.perform(get("/test/authoritative/ORG-A").header("Authorization", bearer(custodian)))
        .andExpect(status().isOk());
    mvc.perform(get("/test/authoritative/ORG-B").header("Authorization", bearer(custodian)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/test/preview/ORG-A").header("Authorization", bearer(custodian)))
        .andExpect(status().isForbidden());
    request(Map.of("scope", "unrelated")).andExpect(status().isForbidden());
  }

  @Test
  void untrustedClientSubjectOrDelegatedHumanIsRejected() throws Exception {
    request(Map.of("client_id", "other-service")).andExpect(status().isUnauthorized());
    request(Map.of("sub", "other-subject")).andExpect(status().isUnauthorized());
    request(Map.of("identity_kind", "user")).andExpect(status().isUnauthorized());
  }
}
