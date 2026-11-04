package com.hexaware.valuation_accural.client;

import com.hexaware.valuation_accural.config.MarketDataProperties;
import com.hexaware.valuation_accural.exception.CalculationException;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MarketDataClient {
  private final RestClient restClient;

  public MarketDataClient(RestClient.Builder builder, MarketDataProperties properties) {
    restClient = builder.baseUrl(properties.getBaseUrl()).build();
  }

  public SecurityReference security(String isin) {
    try {
      SecurityReference result =
          restClient.get().uri("/api/securities/{isin}", isin).retrieve()
              .onStatus(status -> status.value() == 404,
                  (request, response) -> { throw new CalculationException(HttpStatus.UNPROCESSABLE_ENTITY, "Security reference not found"); })
              .onStatus(HttpStatusCode::isError,
                  (request, response) -> { throw CalculationException.unavailable("Security reference service returned an error"); })
              .body(SecurityReference.class);
      if (result == null) throw CalculationException.unavailable("Security reference response was empty");
      return result;
    } catch (RestClientException exception) {
      throw CalculationException.unavailable("Security reference service is unavailable");
    }
  }

  public PriceReference latestPrice(String isin) {
    try {
      PriceReference result =
          restClient.get().uri("/api/securities/{isin}/prices/latest", isin).retrieve()
              .onStatus(status -> status.value() == 404,
                  (request, response) -> { throw CalculationException.unavailable("No market price is available"); })
              .onStatus(HttpStatusCode::isError,
                  (request, response) -> { throw CalculationException.unavailable("Market price service returned an error"); })
              .body(PriceReference.class);
      if (result == null) throw CalculationException.unavailable("Market price response was empty");
      return result;
    } catch (RestClientException exception) {
      throw CalculationException.unavailable("Market price service is unavailable");
    }
  }

  public PriceReference priceAtOrBefore(String isin, Instant cutoff) {
    try {
      PriceReference result =
          restClient.get()
              .uri(builder -> builder.path("/api/securities/{isin}/prices/as-of")
                  .queryParam("at", cutoff)
                  .build(isin))
              .retrieve()
              .onStatus(status -> status.value() == 404,
                  (request, response) -> {
                    throw new CalculationException(
                        HttpStatus.SERVICE_UNAVAILABLE, "No historical market price is available");
                  })
              .onStatus(HttpStatusCode::isError,
                  (request, response) -> {
                    throw CalculationException.unavailable(
                        "Historical market price service returned an error");
                  })
              .body(PriceReference.class);
      if (result == null) {
        throw CalculationException.unavailable("Historical price response was empty");
      }
      return result;
    } catch (RestClientException exception) {
      throw CalculationException.unavailable("Historical market price service is unavailable");
    }
  }

  @Data
  public static class SecurityReference {
    private String isin;
    private String securityType;
    private String currency;
    private String status;
  }

  @Data
  public static class PriceReference {
    private String priceReference;
    private String isin;
    private String currency;
    private BigDecimal marketPrice;
    private Instant priceTimestamp;
    private String sourceName;
    private boolean stale;
  }
}
