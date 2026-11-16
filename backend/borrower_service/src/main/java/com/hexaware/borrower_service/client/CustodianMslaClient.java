package com.hexaware.borrower_service.client;

import com.hexaware.borrower_service.dto.response.BorrowerEligibleMslaResponse;
import com.hexaware.borrower_service.exception.BorrowerException;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CustodianMslaClient {
  private static final ParameterizedTypeReference<List<BorrowerEligibleMslaResponse>> LIST_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;

  public CustodianMslaClient(@Qualifier("custodianMslaRestClient") RestClient custodianMslaRestClient) {
    this.restClient = custodianMslaRestClient;
  }

  public List<BorrowerEligibleMslaResponse> eligible(Authentication authentication) {
    try {
      List<BorrowerEligibleMslaResponse> result =
          restClient
              .get()
              .uri("/api/borrower/mslas")
              .headers(headers -> bearer(headers, authentication))
              .retrieve()
              .body(LIST_TYPE);
      if (result == null) throw BorrowerException.unavailable("Custodian returned no MSLA data");
      return result;
    } catch (RestClientResponseException exception) {
      throw translate(exception);
    } catch (ResourceAccessException exception) {
      throw BorrowerException.unavailable("Custodian MSLA service is unavailable");
    }
  }

  public BorrowerEligibleMslaResponse require(
      Authentication authentication, String reference) {
    try {
      BorrowerEligibleMslaResponse result =
          restClient
              .get()
              .uri("/api/borrower/mslas/{reference}", reference)
              .headers(headers -> bearer(headers, authentication))
              .retrieve()
              .body(BorrowerEligibleMslaResponse.class);
      if (result == null) throw BorrowerException.unavailable("Custodian returned no MSLA data");
      return result;
    } catch (RestClientResponseException exception) {
      throw translate(exception);
    } catch (ResourceAccessException exception) {
      throw BorrowerException.unavailable("Custodian MSLA service is unavailable");
    }
  }

  private void bearer(HttpHeaders headers, Authentication authentication) {
    if (!(authentication instanceof JwtAuthenticationToken jwt)
        || !jwt.isAuthenticated()
        || jwt.getToken().getTokenValue().isBlank()) {
      throw BorrowerException.invalid("Authenticated borrower JWT required");
    }
    headers.setBearerAuth(jwt.getToken().getTokenValue());
  }

  private BorrowerException translate(RestClientResponseException exception) {
    return switch (exception.getStatusCode().value()) {
      case 401, 403 -> new BorrowerException(
          org.springframework.http.HttpStatus.FORBIDDEN, "Custodian denied borrower access");
      case 404 -> BorrowerException.conflict("MSLA is not eligible for this borrower");
      default -> exception.getStatusCode().is5xxServerError()
          ? BorrowerException.unavailable("Custodian MSLA service returned an error")
          : BorrowerException.conflict("Custodian rejected the MSLA request");
    };
  }
}
