package com.hexaware.custodian_service.client;

import com.hexaware.custodian_service.exception.SetupException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.time.Instant;

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

  public List<SecurityReferenceResponse> listActive() {
    try {
      SecurityReferencePageResponse page = securityMarketRestClient.get()
          .uri(builder -> builder.path("/api/securities")
              .queryParam("activeOnly", true).queryParam("page", 0).queryParam("size", 100)
              .build())
          .retrieve()
          .body(SecurityReferencePageResponse.class);
      if (page == null || page.content() == null) {
        throw SetupException.unavailable("Security reference service returned no data");
      }
      return page.content();
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Security reference service is unavailable");
    }
  }

  public SecurityPriceResponse latestPrice(String isin) {
    try {
      SecurityPriceResponse price = securityMarketRestClient.get()
          .uri("/api/securities/{isin}/prices/latest", isin)
          .retrieve()
          .body(SecurityPriceResponse.class);
      if (price == null) throw SetupException.unavailable("Security market returned no current quote");
      return price;
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Security market price service is unavailable");
    }
  }

  public SecurityPricePageResponse priceHistory(String isin, Instant from, Instant to) {
    try {
      SecurityPricePageResponse page = securityMarketRestClient.get()
          .uri(builder -> builder.path("/api/securities/{isin}/prices")
              .queryParam("from", from).queryParam("to", to)
              .queryParam("page", 0).queryParam("size", 200).build(isin))
          .retrieve()
          .body(SecurityPricePageResponse.class);
      if (page == null || page.content() == null) throw SetupException.unavailable("Security market returned no price history");
      return page;
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Security market price history service is unavailable");
    }
  }
}
