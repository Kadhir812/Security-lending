package com.hexaware.custodian_service.security.authorization;

import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.OrganizationStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.security.authentication.LocalUserAuthentication;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BorrowerAccess {
  private final OrganizationRepository organizations;

  public Organization borrower(Authentication authentication) {
    if (!(authentication instanceof LocalUserAuthentication user)
        || !user.isAuthenticated()
        || user.getAuthorities().stream()
            .noneMatch(authority -> authority.getAuthority().equals("ROLE_TRADER"))) {
      throw new AccessDeniedException("Authenticated borrower required");
    }

    Organization borrower =
        organizations
            .findByOrganizationReference(user.getOrganizationReference())
            .orElseThrow(() -> new AccessDeniedException("Borrower access denied"));
    if (borrower.getStatus() != OrganizationStatus.ACTIVE
        || borrower.getOrganizationType() != OrganizationType.BORROWER) {
      throw new AccessDeniedException("Active borrower required");
    }
    return borrower;
  }
}
