package com.hexaware.custodian_service.security.authorization;

import com.hexaware.custodian_service.security.authentication.LocalUserAuthentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class OrganizationAuthorization {
  public void requireOrganization(Authentication authentication, String resourceOrganization) {
    if (!(authentication instanceof LocalUserAuthentication user)
        || !user.isAuthenticated()
        || resourceOrganization == null
        || !user.getOrganizationReference().equals(resourceOrganization)) {
      throw new AccessDeniedException("Organization access denied");
    }
  }

  public void requireRole(Authentication authentication, String role) {
    if (!(authentication instanceof LocalUserAuthentication)
        || !authentication.isAuthenticated()
        || authentication.getAuthorities().stream()
            .noneMatch(a -> a.getAuthority().equals("ROLE_" + role))) {
      throw new AccessDeniedException("Role required");
    }
  }
}
