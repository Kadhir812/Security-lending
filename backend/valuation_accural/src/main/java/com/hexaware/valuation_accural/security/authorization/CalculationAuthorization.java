package com.hexaware.valuation_accural.security.authorization;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CalculationAuthorization {
  public void requirePreview(Authentication authentication, String organization) {
    require(authentication, organization, "valuation.preview");
  }

  public void requireAuthoritative(Authentication authentication, String organization) {
    require(authentication, organization, "valuation.authoritative");
  }

  private void require(Authentication authentication, String organization, String scope) {
    if (!(authentication instanceof JwtAuthenticationToken token)
        || !token.isAuthenticated()
        || organization == null
        || !organization.equals(token.getToken().getClaimAsString("organization_reference"))
        || token.getAuthorities().stream()
            .noneMatch(a -> a.getAuthority().equals("SCOPE_" + scope))) {
      throw new AccessDeniedException("Calculation permission or organization scope denied");
    }
  }
}
