package com.hexaware.custodian_service.service.organization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.dto.request.BorrowerIdentityLinkRequest;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.BorrowerIdentityLinkRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.RoleRepository;
import com.hexaware.custodian_service.repository.UserRepository;
import com.hexaware.custodian_service.repository.UserRoleRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class BorrowerIdentityLinkServiceTest {

  @Test
  void rejectsOrdinaryBorrowerAccessTokenWithoutProvisioning() {
    SetupAccess access = mock(SetupAccess.class);
    Organization custodian = Organization.builder().organizationId(4L).build();
    when(access.actor(any(), eq(true))).thenReturn(custodian);
    when(access.user(any())).thenReturn(null);

    Jwt accessToken =
        Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .issuer("securities-lending")
            .subject("borrower-subject")
            .audience(List.of("borrower-service", "custodian-service"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    JwtDecoder decoder = mock(JwtDecoder.class);
    when(decoder.decode("token")).thenReturn(accessToken);

    var links = mock(BorrowerIdentityLinkRepository.class);
    var organizations = mock(OrganizationRepository.class);
    var users = mock(UserRepository.class);
    var roles = mock(RoleRepository.class);
    var userRoles = mock(UserRoleRepository.class);
    var service =
        new BorrowerIdentityLinkService(
            access,
            decoder,
            links,
            organizations,
            users,
            roles,
            userRoles,
            Clock.systemUTC());

    assertThatThrownBy(
            () -> service.link(mock(Authentication.class), new BorrowerIdentityLinkRequest("token")))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("not a valid Borrower link code");
    verifyNoInteractions(links, organizations, users, roles, userRoles);
  }
}
