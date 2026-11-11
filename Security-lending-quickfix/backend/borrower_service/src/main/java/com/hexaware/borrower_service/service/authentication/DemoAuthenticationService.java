package com.hexaware.borrower_service.service.authentication;

import com.hexaware.borrower_service.dto.request.DemoSignupRequest;
import com.hexaware.borrower_service.dto.request.LoginRequest;
import com.hexaware.borrower_service.dto.response.AuthResponse;
import com.hexaware.borrower_service.entity.Role;
import com.hexaware.borrower_service.entity.User;
import com.hexaware.borrower_service.entity.UserRole;
import com.hexaware.borrower_service.enums.UserStatus;
import com.hexaware.borrower_service.exception.BorrowerException;
import com.hexaware.borrower_service.repository.RoleRepository;
import com.hexaware.borrower_service.repository.UserRepository;
import com.hexaware.borrower_service.repository.UserRoleRepository;
import com.hexaware.borrower_service.security.authentication.BorrowerTokenIssuer;
import com.hexaware.borrower_service.security.authentication.LocalUserAuthentication;
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

  private static final String TRADER_ROLE = "TRADER";
  private final UserRepository users;
  private final RoleRepository roles;
  private final UserRoleRepository userRoles;
  private final PasswordEncoder passwordEncoder;
  private final BorrowerTokenIssuer tokenIssuer;

  @Transactional
  public AuthResponse signup(DemoSignupRequest request) {
    String email = normalizeEmail(request.email());
    validatePasswordBytes(request.password());
    if (users.existsByEmailIgnoreCase(email)) {
      throw BorrowerException.conflict("An account with this email already exists.");
    }
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

    String organizationReference =
        "BORROWER-" + UUID.randomUUID().toString().replace("-", "");
    User user =
        users.save(
            User.builder()
                .identityIssuer(tokenIssuer.issuer())
                .identitySubject(UUID.randomUUID().toString())
                .organizationReference(organizationReference)
                .organizationName(request.organizationName().trim())
                .username(request.username().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .build());
    userRoles.save(UserRole.builder().user(user).role(trader).build());
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
    boolean canTrade =
        user.getStatus() == UserStatus.ACTIVE
            && userRoles.findByUserUserId(user.getUserId()).stream()
                .anyMatch(assignment -> TRADER_ROLE.equals(assignment.getRole().getRoleName()));
    if (!canTrade) {
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
        user.getOrganizationName(),
        user.getOrganizationReference());
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static void validatePasswordBytes(String password) {
    if (!passwordFitsEncoder(password)) {
      throw BorrowerException.invalid("Password must be no more than 72 UTF-8 bytes.");
    }
  }

  private static boolean passwordFitsEncoder(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= 72;
  }

  private static ResponseStatusException invalidCredentials() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
  }
}
