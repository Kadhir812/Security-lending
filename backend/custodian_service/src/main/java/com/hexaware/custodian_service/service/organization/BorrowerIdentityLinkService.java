package com.hexaware.custodian_service.service.organization;

import com.hexaware.custodian_service.dto.request.BorrowerIdentityLinkRequest;
import com.hexaware.custodian_service.dto.response.BorrowerIdentityLinkResponse;
import com.hexaware.custodian_service.entity.BorrowerIdentityLink;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.Role;
import com.hexaware.custodian_service.entity.User;
import com.hexaware.custodian_service.entity.UserRole;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.UserStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.BorrowerIdentityLinkRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.RoleRepository;
import com.hexaware.custodian_service.repository.UserRepository;
import com.hexaware.custodian_service.repository.UserRoleRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BorrowerIdentityLinkService {

  private static final String LINK_PURPOSE = "custodian-identity-link";
  private static final String TRADER_ROLE = "TRADER";

  private final SetupAccess access;
  private final JwtDecoder jwtDecoder;
  private final BorrowerIdentityLinkRepository links;
  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final RoleRepository roles;
  private final UserRoleRepository userRoles;
  private final Clock clock;

  @Transactional
  public BorrowerIdentityLinkResponse link(
      Authentication authentication, BorrowerIdentityLinkRequest request) {
    Organization custodian = access.actor(authentication, true);
    var operator = access.user(authentication);
    Jwt token = decode(request.linkToken());

    if (!LINK_PURPOSE.equals(token.getClaimAsString("purpose"))) {
      throw SetupException.invalid("This is not a valid Borrower link code");
    }

    String jti = token.getId();
    try {
      UUID.fromString(jti);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw SetupException.invalid("This Borrower link code is invalid");
    }
    if (links.existsById(jti)) {
      throw SetupException.conflict("This Borrower link code has already been used");
    }

    String reference = SetupValidation.reference(token.getClaimAsString("organization_reference"));
    String name = SetupValidation.text(token.getClaimAsString("organization_name"), "organizationName", 255);
    String subject = SetupValidation.text(token.getSubject(), "subject", 255);
    String issuer = SetupValidation.text(token.getClaimAsString("iss"), "issuer", 255);
    String username = SetupValidation.text(token.getClaimAsString("username"), "username", 100);
    String email = SetupValidation.text(token.getClaimAsString("email"), "email", 255);

    Organization borrower =
        organizations
            .findLocked(reference)
            .orElseGet(
                () ->
                    organizations.saveAndFlush(
                        Organization.builder()
                            .organizationReference(reference)
                            .organizationName(name)
                            .organizationType(OrganizationType.BORROWER)
                            .status(OrganizationStatus.ACTIVE)
                            .registeredByCustodianId(custodian.getOrganizationId())
                            .createdByUserId(operator.getUserId())
                            .createdAt(clock.instant())
                            .updatedAt(clock.instant())
                            .updatedByUserId(operator.getUserId())
                            .build()));

    if (borrower.getOrganizationType() != OrganizationType.BORROWER
        || borrower.getStatus() != OrganizationStatus.ACTIVE
        || !borrower.getOrganizationName().equals(name)) {
      throw SetupException.conflict(
          "Borrower reference already exists with different organization details");
    }
    access.managed(borrower, custodian.getOrganizationId());

    if (users.findByIdentityIssuerAndIdentitySubject(issuer, subject).isPresent()) {
      throw SetupException.conflict("This Borrower identity is already linked");
    }
    if (users.existsByEmailIgnoreCase(email)
        || users.existsByOrganizationOrganizationIdAndUsername(
            borrower.getOrganizationId(), username)) {
      throw SetupException.conflict("Borrower email or username is already registered in Custodian");
    }

    links.saveAndFlush(
        BorrowerIdentityLink.builder()
            .jti(jti)
            .organizationReference(reference)
            .consumedByCustodianId(custodian.getOrganizationId())
            .consumedByUserId(operator.getUserId())
            .consumedAt(clock.instant())
            .build());
    Role trader =
        roles
            .findByRoleName(TRADER_ROLE)
            .orElseGet(
                () ->
                    roles.save(
                        Role.builder()
                            .roleName(TRADER_ROLE)
                            .description("Borrower application user")
                            .build()));
    User borrowerUser =
        users.save(
            User.builder()
                .organization(borrower)
                .identityIssuer(issuer)
                .identitySubject(subject)
                .username(username)
                .email(email)
                .status(UserStatus.ACTIVE)
                .createdAt(clock.instant())
                .build());
    userRoles.save(UserRole.builder().user(borrowerUser).role(trader).build());

    return new BorrowerIdentityLinkResponse("LINKED", reference, name, username, email);
  }

  private Jwt decode(String token) {
    try {
      Jwt jwt = jwtDecoder.decode(token);
      if (jwt.getAudience() == null || !jwt.getAudience().contains("borrower-service")) {
        throw SetupException.invalid("Borrower link code has the wrong audience");
      }
      return jwt;
    } catch (JwtException exception) {
      throw SetupException.invalid("Borrower link code is invalid or expired");
    }
  }
}
