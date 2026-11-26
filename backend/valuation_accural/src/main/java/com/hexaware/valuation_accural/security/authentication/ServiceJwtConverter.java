package com.hexaware.valuation_accural.security.authentication;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

@Component
public class ServiceJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {
  private final String previewClient, previewSubject, custodianClient, custodianSubject;

  public ServiceJwtConverter(
      @Value("${app.auth.preview-client-id}") String previewClient,
      @Value("${app.auth.preview-subject}") String previewSubject,
      @Value("${app.auth.custodian-client-id}") String custodianClient,
      @Value("${app.auth.custodian-subject}") String custodianSubject) {
    if (previewSubject.isBlank()
        || custodianSubject.isBlank()
        || previewClient.isBlank()
        || custodianClient.isBlank()
        || previewClient.equals(custodianClient)
        || previewSubject.equals(custodianSubject)) {
      throw new IllegalArgumentException("Distinct trusted service identities are required");
    }
    this.previewClient = previewClient;
    this.previewSubject = previewSubject;
    this.custodianClient = custodianClient;
    this.custodianSubject = custodianSubject;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String client = jwt.getClaimAsString("client_id");
    String identityKind = jwt.getClaimAsString("identity_kind");
    boolean custodian = custodianClient.equals(client) && custodianSubject.equals(jwt.getSubject());
    boolean preview = previewClient.equals(client) && previewSubject.equals(jwt.getSubject());
    boolean delegatedCustodian =
        "human".equals(identityKind)
            && new JwtGrantedAuthoritiesConverter()
                .convert(jwt).stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_valuation.authoritative"));
    if (!("service".equals(identityKind) && (custodian || preview)) && !delegatedCustodian) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("invalid_token", "Trusted valuation identity required", null));
    }
    var authorities =
        new JwtGrantedAuthoritiesConverter()
            .convert(jwt).stream()
                .filter(
                    a ->
                        a.getAuthority().equals("SCOPE_valuation.preview")
                            || (custodian
                            && a.getAuthority().equals("SCOPE_valuation.authoritative"))
                        || (delegatedCustodian
                            && a.getAuthority().equals("SCOPE_valuation.authoritative")))
                .toList();
    return new JwtAuthenticationToken(jwt, authorities);
  }
}
