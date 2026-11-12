package com.hexaware.custodian_service.service.authentication;

import com.hexaware.custodian_service.dto.request.DemoSignupRequest;
import com.hexaware.custodian_service.dto.request.LoginRequest;
import com.hexaware.custodian_service.dto.response.AuthResponse;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.Role;
import com.hexaware.custodian_service.entity.User;
import com.hexaware.custodian_service.entity.UserRole;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.UserStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.RoleRepository;
import com.hexaware.custodian_service.repository.UserRepository;
import com.hexaware.custodian_service.repository.UserRoleRepository;
import com.hexaware.custodian_service.security.authentication.CustodianTokenIssuer;
import com.hexaware.custodian_service.security.authentication.LocalUserAuthentication;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DemoAuthenticationService {

  private static final String OPERATOR_ROLE = "CUSTODIAN_OPERATOR";
  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final RoleRepository roles;
  private final UserRoleRepository userRoles;
  private final PasswordEncoder passwordEncoder;
  private final CustodianTokenIssuer tokenIssuer;

  @Transactional
  public AuthResponse signup(DemoSignupRequest request) {
    String email = normalizeEmail(request.email());
    validatePasswordBytes(request.password());
    if (users.existsByEmailIgnoreCase(email)) {
      throw SetupException.conflict("An account with this email already exists.");
    }
    Role operator =
        roles
            .findByRoleName(OPERATOR_ROLE)
            .orElseGet(
                () ->
                    roles.save(
                        Role.builder()
                            .roleName(OPERATOR_ROLE)
                            .description("Custodian application operator")
                            .build()));

    Organization organization =
        organizations.save(
            Organization.builder()
                .organizationReference(
                    "CUSTODIAN-" + UUID.randomUUID().toString().replace("-", ""))
                .organizationName(request.organizationName().trim())
                .organizationType(OrganizationType.CUSTODIAN)
                .status(OrganizationStatus.ACTIVE)
                .build());
    User user =
        users.save(
            User.builder()
                .organization(organization)
                .identityIssuer(tokenIssuer.issuer())
                .identitySubject(UUID.randomUUID().toString())
                .username(request.username().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .build());
    userRoles.save(UserRole.builder().user(user).role(operator).build());
    return response(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    String email = normalizeEmail(request.email());
    if (!passwordFitsEncoder(request.password())) {
      throw invalidCredentials();
    }
    User user =
        users
            .findByEmailIgnoreCase(email)
            .filter(value -> value.getPasswordHash() != null)
            .filter(value -> passwordEncoder.matches(request.password(), value.getPasswordHash()))
            .orElseThrow(DemoAuthenticationService::invalidCredentials);
    boolean canOperate =
        user.getStatus() == UserStatus.ACTIVE
            && user.getOrganization().getStatus() == OrganizationStatus.ACTIVE
            && userRoles.findByUserUserId(user.getUserId()).stream()
                .anyMatch(assignment -> OPERATOR_ROLE.equals(assignment.getRole().getRoleName()));
    if (!canOperate) {
      throw invalidCredentials();
    }
    return response(user);
  }

  @Transactional(readOnly = true)
  public AuthResponse.UserView current(LocalUserAuthentication authentication) {
    User user =
        users
            .findById(authentication.getUserId())
            .orElseThrow(DemoAuthenticationService::invalidCredentials);
    return view(user);
  }

  private AuthResponse response(User user) {
    return new AuthResponse(
        tokenIssuer.issue(user), "Bearer", tokenIssuer.lifetimeSeconds(), view(user));
  }

  private AuthResponse.UserView view(User user) {
    return new AuthResponse.UserView(
        user.getUsername(),
        user.getEmail(),
        user.getOrganization().getOrganizationName(),
        user.getOrganization().getOrganizationReference());
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static void validatePasswordBytes(String password) {
    if (!passwordFitsEncoder(password)) {
      throw SetupException.invalid("Password must be no more than 72 UTF-8 bytes.");
    }
  }

  private static boolean passwordFitsEncoder(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  private static ResponseStatusException invalidCredentials() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
  }
}
