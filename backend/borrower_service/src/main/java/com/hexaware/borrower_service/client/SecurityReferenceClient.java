package com.hexaware.borrower_service.client;

import com.hexaware.borrower_service.exception.BorrowerException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.time.Instant;
import java.util.List;

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

  public List<BorrowerSecurityReference> listActive() {
    try {
      BorrowerSecurityReferencePage page = restClient.get()
          .uri(builder -> builder.path("/api/securities")
              .queryParam("activeOnly", true).queryParam("page", 0).queryParam("size", 100)
              .build())
          .retrieve()
          .body(BorrowerSecurityReferencePage.class);
      if (page == null || page.content() == null) {
        throw BorrowerException.unavailable("Security reference service returned no data");
      }
      return page.content();
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().is5xxServerError()) {
        throw BorrowerException.unavailable("Security reference service returned an error");
      }
      throw BorrowerException.unavailable("Security reference list request was rejected");
    } catch (ResourceAccessException exception) {
      throw BorrowerException.unavailable("Security reference service is unavailable");
    }
  }

  public BorrowerSecurityPrice latestPrice(String isin) {
    try {
      BorrowerSecurityPrice price = restClient.get()
          .uri("/api/securities/{isin}/prices/latest", isin)
          .retrieve().body(BorrowerSecurityPrice.class);
      if (price == null) throw BorrowerException.unavailable("Market service returned no quote");
      return price;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404)
        throw BorrowerException.unavailable("No current market quote is available for this security");
      throw BorrowerException.unavailable("Market price service returned an error");
    } catch (ResourceAccessException exception) {
      throw BorrowerException.unavailable("Market price service is unavailable");
    }
  }

  public BorrowerSecurityPricePage priceHistory(
      String isin, Instant from, Instant to, int page, int size) {
    try {
      BorrowerSecurityPricePage result = restClient.get()
          .uri(builder -> builder.path("/api/securities/{isin}/prices")
              .queryParam("from", from).queryParam("to", to)
              .queryParam("page", page).queryParam("size", size).build(isin))
          .retrieve().body(BorrowerSecurityPricePage.class);
      if (result == null) throw BorrowerException.unavailable("Market service returned no price history");
      return result;
    } catch (RestClientResponseException exception) {
      throw BorrowerException.unavailable("Market price history service returned an error");
    } catch (ResourceAccessException exception) {
      throw BorrowerException.unavailable("Market price history service is unavailable");
    }
  }
}
