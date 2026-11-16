package com.hexaware.custodian_service.security.authentication;

import java.util.Collection;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Getter
public class LocalUserAuthentication extends JwtAuthenticationToken {
  private final Long userId;
  private final String organizationReference;

  public LocalUserAuthentication(
      Jwt jwt,
      Collection<? extends GrantedAuthority> authorities,
      Long userId,
      String organizationReference) {
    super(jwt, authorities);
    this.userId = userId;
    this.organizationReference = organizationReference;
  }
}
