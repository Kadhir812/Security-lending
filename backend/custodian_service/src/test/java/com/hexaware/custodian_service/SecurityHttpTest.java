package com.hexaware.custodian_service;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexaware.custodian_service.controller.FoundationController;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.Role;
import com.hexaware.custodian_service.entity.User;
import com.hexaware.custodian_service.entity.UserRole;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.UserStatus;
import com.hexaware.custodian_service.exception.ApiExceptionHandler;
import com.hexaware.custodian_service.repository.MandateAccessRepository;
import com.hexaware.custodian_service.repository.UserRepository;
import com.hexaware.custodian_service.repository.UserRoleRepository;
import com.hexaware.custodian_service.security.authentication.LocalUserJwtConverter;
import com.hexaware.custodian_service.security.authentication.TokenValidation;
import com.hexaware.custodian_service.security.authorization.MandateAuthorization;
import com.hexaware.custodian_service.security.authorization.OrganizationAuthorization;
import com.hexaware.custodian_service.security.config.SecurityConfiguration;
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
import java.util.Optional;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    LocalUserJwtConverter.class,
    OrganizationAuthorization.class,
    MandateAuthorization.class
  })
  static class Config {
    @Bean
    JwtDecoder jwtDecoder() {
      var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY.getPublic()).build();
      decoder.setJwtValidator(TokenValidation.validator(ISSUER, "custodian-service"));
      return decoder;
    }

    @Bean
    UserRepository users() {
      return mock(UserRepository.class);
    }

    @Bean
    UserRoleRepository roles() {
      return mock(UserRoleRepository.class);
    }

    @Bean
    MandateAccessRepository mandates() {
      return mock(MandateAccessRepository.class);
    }

    @Bean
    java.time.Clock clock() {
      return java.time.Clock.systemUTC();
    }
  }

  @TestComponent
  @RestController
  static class Checks {
    private final OrganizationAuthorization authorization;

    Checks(OrganizationAuthorization authorization) {
      this.authorization = authorization;
    }

    @GetMapping("/test/organizations/{organization}")
    void organization(Authentication user, @PathVariable String organization) {
      authorization.requireOrganization(user, organization);
    }

    @GetMapping("/api/borrower/mslas")
    void borrowerMsla(Authentication user) {}

    @PostMapping("/test/organizations")
    void body(Authentication user, @RequestBody Map<String, String> body) {
      authorization.requireOrganization(user, body.get("organization_reference"));
    }

    @Autowired MandateAuthorization mandates;

    @GetMapping("/test/mandates/{reference}")
    void mandate(Authentication user, @PathVariable String reference) {
      mandates.requireMandate(user, reference);
    }
  }

  @Autowired WebApplicationContext context;
  @Autowired UserRepository users;
  @Autowired UserRoleRepository roles;
  User user;
  @Autowired MandateAccessRepository mandates;
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    reset(users, roles);
    user =
        User.builder()
            .userId(1L)
            .identityIssuer(ISSUER)
            .identitySubject("subject-a")
            .username("alice")
            .email("alice@example.test")
            .organization(
                Organization.builder()
                    .organizationId(1L)
                    .organizationReference("ORG-A")
                    .organizationName("Custodian A")
                    .organizationType(OrganizationType.CUSTODIAN)
                    .build())
            .build();
    when(users.findByIdentityIssuerAndIdentitySubject(ISSUER, "subject-a"))
        .thenReturn(Optional.of(user));
    when(roles.findByUserUserId(1L))
        .thenReturn(
            List.of(
                UserRole.builder()
                    .user(user)
                    .role(Role.builder().roleName("CUSTODIAN_OPERATOR").build())
                    .build()));
    reset(mandates);
    when(mandates.isAccessible(eq("MANDATE-A"), eq("ORG-A"), any())).thenReturn(true);
  }

  String bearer(Map<String, Object> changes) throws Exception {
    return "Bearer " + token(changes, KEY);
  }

  String token(Map<String, Object> changes, KeyPair key) throws Exception {
    var claims = new HashMap<String, Object>();
    claims.put("iss", ISSUER);
    claims.put("aud", List.of("custodian-service"));
    claims.put("exp", Instant.now().plusSeconds(120).getEpochSecond());
    claims.put("nbf", Instant.now().minusSeconds(1).getEpochSecond());
    claims.put("organization_reference", "ORG-A");
    claims.put("sub", "subject-a");
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
  void inactiveUnknownAndCaseMismatchedUsersAreRejected() throws Exception {
    user.setStatus(UserStatus.INACTIVE);
    request(Map.of()).andExpect(status().isUnauthorized());
    user.setStatus(UserStatus.ACTIVE);
    request(Map.of("sub", "unknown")).andExpect(status().isUnauthorized());
    user.setIdentitySubject("SUBJECT-A");
    request(Map.of()).andExpect(status().isUnauthorized());
  }

  @Test
  void tokenRolesCannotReplaceLocalPermissions() throws Exception {
    when(roles.findByUserUserId(1L)).thenReturn(List.of());
    request(Map.of("roles", List.of("ADMINISTRATOR"))).andExpect(status().isForbidden());
  }

  @Test
  void organizationImpersonationIsRejectedInTokenPathAndBody() throws Exception {
    request(Map.of("organization_reference", "ORG-B")).andExpect(status().isUnauthorized());
    mvc.perform(get("/test/organizations/ORG-A").header("Authorization", bearer(Map.of())))
        .andExpect(status().isOk());
    mvc.perform(get("/test/organizations/ORG-B").header("Authorization", bearer(Map.of())))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/test/organizations")
                .header("Authorization", bearer(Map.of()))
                .contentType("application/json")
                .content("{\"organization_reference\":\"ORG-B\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void mandateMustBelongToAuthenticatedCustodianAndBeEffective() throws Exception {
    mvc.perform(get("/test/mandates/MANDATE-A").header("Authorization", bearer(Map.of())))
        .andExpect(status().isOk());
    mvc.perform(get("/test/mandates/MANDATE-B").header("Authorization", bearer(Map.of())))
        .andExpect(status().isForbidden());
  }

  @Test
  void inactiveOrWrongTypeOrganizationIsRejected() throws Exception {
    user.getOrganization().setStatus(OrganizationStatus.INACTIVE);
    request(Map.of()).andExpect(status().isUnauthorized());
    user.getOrganization().setStatus(OrganizationStatus.ACTIVE);
    user.getOrganization().setOrganizationType(OrganizationType.BORROWER);
    request(Map.of()).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedBorrowerJwtIsLimitedToBorrowerMslaRoute() throws Exception {
    user.getOrganization().setOrganizationType(OrganizationType.BORROWER);
    when(roles.findByUserUserId(1L))
        .thenReturn(
            List.of(
                UserRole.builder()
                    .user(user)
                    .role(Role.builder().roleName("TRADER").build())
                    .build()));
    mvc.perform(
            get("/api/borrower/mslas")
                .header(
                    "Authorization",
                    bearer(Map.of("aud", List.of("borrower-service", "custodian-service")))))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/foundation/status")
                .header(
                    "Authorization",
                    bearer(Map.of("aud", List.of("borrower-service", "custodian-service")))))
        .andExpect(status().isForbidden());
  }
}
