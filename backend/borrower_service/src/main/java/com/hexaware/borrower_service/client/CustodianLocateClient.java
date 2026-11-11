package com.hexaware.borrower_service.client;

import com.hexaware.borrower_service.dto.request.LocateSubmission;
import com.hexaware.borrower_service.dto.response.LocateResponse;
import com.hexaware.borrower_service.exception.BorrowerException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CustodianLocateClient {
  private final RestClient restClient;

  public CustodianLocateClient(
      @Qualifier("custodianMslaRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  public LocateResponse submit(Authentication authentication, LocateSubmission request) {
    try {
      var response = restClient.post().uri("/api/borrower/locates")
          .headers(headers -> bearer(headers, authentication))
          .body(request).retrieve().body(LocateResponse.class);
      if (response == null) throw BorrowerException.unavailable("Custodian returned no locate data");
      return response;
    } catch (RestClientResponseException ex) {
      throw translate(ex);
    } catch (ResourceAccessException ex) {
      throw BorrowerException.unavailable("Custodian locate service is unavailable");
    }
  }

  public LocateResponse get(Authentication authentication, String reference) {
    try {
      var response = restClient.get().uri("/api/borrower/locates/{reference}", reference)
          .headers(headers -> bearer(headers, authentication))
          .retrieve().body(LocateResponse.class);
      if (response == null) throw BorrowerException.unavailable("Custodian returned no locate data");
      return response;
    } catch (RestClientResponseException ex) {
      throw translate(ex);
    } catch (ResourceAccessException ex) {
      throw BorrowerException.unavailable("Custodian locate service is unavailable");
    }
  }

  private void bearer(HttpHeaders headers, Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken jwt)
        || !jwt.isAuthenticated() || jwt.getToken().getTokenValue().isBlank())
      throw BorrowerException.invalid("Authenticated borrower JWT required");
    headers.setBearerAuth(jwt.getToken().getTokenValue());
  }

  private BorrowerException translate(RestClientResponseException ex) {
    return switch (ex.getStatusCode().value()) {
      case 401, 403 -> new BorrowerException(
          org.springframework.http.HttpStatus.FORBIDDEN, "Custodian denied locate access");
      case 404 -> BorrowerException.missing("Locate not found");
      default -> ex.getStatusCode().is5xxServerError()
          ? BorrowerException.unavailable("Custodian locate service returned an error")
          : BorrowerException.conflict("Custodian rejected the locate request");
    };
  }
}
