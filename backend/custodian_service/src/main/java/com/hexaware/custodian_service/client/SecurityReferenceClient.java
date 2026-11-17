package com.hexaware.custodian_service.client;

import com.hexaware.custodian_service.exception.SetupException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class SecurityReferenceClient {

  private final RestClient securityMarketRestClient;

  public SecurityReferenceResponse require(String isin) {
    try {
      SecurityReferenceResponse security =
          securityMarketRestClient
              .get()
              .uri("/api/securities/{isin}", isin)
              .retrieve()
              .onStatus(
                  status -> status.value() == 404,
                  (request, response) -> {
                    throw SetupException.missing("Unknown security ISIN");
                  })
              .onStatus(
                  HttpStatusCode::isError,
                  (request, response) -> {
                    throw SetupException.unavailable(
                        "Security reference service returned an error");
                  })
              .body(SecurityReferenceResponse.class);
      if (security == null) {
        throw SetupException.unavailable("Security reference service returned an empty response");
      }
      return security;
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Security reference service is unavailable");
    }
  }
}
