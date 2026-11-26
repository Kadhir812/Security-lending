package com.hexaware.custodian_service.security.authorization;

import com.hexaware.custodian_service.repository.MandateAccessRepository;
import com.hexaware.custodian_service.security.authentication.LocalUserAuthentication;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MandateAuthorization {
  private final MandateAccessRepository mandates;
  private final OrganizationAuthorization organizations;
  private final Clock clock;

  public void requireMandate(Authentication authentication, String mandateReference) {
    organizations.requireRole(authentication, "CUSTODIAN_OPERATOR");
    var user = (LocalUserAuthentication) authentication;
    if (!mandates.isAccessible(
        mandateReference, user.getOrganizationReference(), LocalDate.now(clock))) {
      throw new AccessDeniedException("Mandate access denied");
    }
  }
}
