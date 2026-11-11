package com.hexaware.custodian_service.security.authentication;

import com.hexaware.custodian_service.entity.User;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.UserStatus;
import com.hexaware.custodian_service.repository.UserRepository;
import com.hexaware.custodian_service.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LocalUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
  private final UserRepository users;
  private final UserRoleRepository roles;

  @Override
  @Transactional(readOnly = true)
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String issuer = jwt.getClaimAsString("iss");
    User user =
        users
            .findByIdentityIssuerAndIdentitySubject(issuer, jwt.getSubject())
            .orElseThrow(this::invalid);
    // MySQL's supplied collation is case-insensitive; identity matching must not be.
    if (!issuer.equals(user.getIdentityIssuer())
        || !jwt.getSubject().equals(user.getIdentitySubject())
        || user.getStatus() != UserStatus.ACTIVE) throw invalid();

    String organization = user.getOrganization().getOrganizationReference();
    if (!organization.equals(jwt.getClaimAsString("organization_reference"))) throw invalid();
    var authorities =
        roles.findByUserUserId(user.getUserId()).stream()
            .map(
                assignment ->
                    new SimpleGrantedAuthority("ROLE_" + assignment.getRole().getRoleName()))
            .distinct()
            .toList();
    boolean active = user.getOrganization().getStatus() == OrganizationStatus.ACTIVE;
    boolean authorizedBorrower =
        user.getOrganization().getOrganizationType() == OrganizationType.BORROWER
            && authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_TRADER"));
    boolean custodianOrganization =
        user.getOrganization().getOrganizationType() == OrganizationType.CUSTODIAN;
    if (!active || !(authorizedBorrower || custodianOrganization)) throw invalid();
    // Token role claims never grant local permissions.
    return new LocalUserAuthentication(jwt, authorities, user.getUserId(), organization);
  }

  private OAuth2AuthenticationException invalid() {
    return new OAuth2AuthenticationException(
        new OAuth2Error("invalid_token", "Local identity is not authorized", null));
  }
}
