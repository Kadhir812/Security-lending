package com.hexaware.borrower_service.client;

import com.hexaware.borrower_service.exception.BorrowerException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SecurityReferenceClient {
  private final RestClient restClient;

  public SecurityReferenceClient(@Qualifier("securityMarketRestClient") RestClient securityMarketRestClient) {
    this.restClient = securityMarketRestClient;
  }

  public BorrowerSecurityReference require(String isin) {
    try {
      BorrowerSecurityReference security =
          restClient
              .get()
              .uri("/api/securities/{isin}", isin)
              .retrieve()
              .body(BorrowerSecurityReference.class);
      if (security == null) {
        throw BorrowerException.unavailable("Security reference service returned no data");
      }
      return security;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        throw BorrowerException.invalid("Unknown security ISIN");
      }
      if (exception.getStatusCode().is5xxServerError()) {
        throw BorrowerException.unavailable("Security reference service returned an error");
      }
      throw BorrowerException.invalid("Security reference request was rejected");
    } catch (ResourceAccessException exception) {
      throw BorrowerException.unavailable("Security reference service is unavailable");
    }
  }
}
