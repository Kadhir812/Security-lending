package com.hexaware.custodian_service.client;

import com.hexaware.custodian_service.exception.SetupException;
import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class ValuationCalculationClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(ValuationCalculationClient.class);
  private static final int MAX_ERROR_BODY_LENGTH = 512;

  private final RestClient.Builder builder;

  @Value("${app.valuation.base-url:http://localhost:8083}")
  private String baseUrl;

  public ValuationCalculationResponse calculate(
      ValuationCalculationRequest request, String bearerToken) {
    try {
      var response =
          builder
              .baseUrl(baseUrl)
              .build()
              .post()
              .uri("/api/calculations/loan-approval")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
              .body(request)
              .retrieve()
              .body(ValuationCalculationResponse.class);
      if (response == null) {
        throw SetupException.unavailable("Valuation service returned an empty response");
      }
      return response;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
        LOGGER.warn(
            "Valuation loan approval rejected with HTTP 422: {}",
            boundedResponseBody(exception));
        throw SetupException.conflict("Valuation inputs are not eligible for approval");
      }
      throw SetupException.unavailable("Valuation service rejected or could not process approval");
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Valuation service is unavailable");
    }
  }

  public MtmCalculationResponse calculateMtm(
      MtmCalculationRequest request, String bearerToken) {
    try {
      var response =
          builder
              .baseUrl(baseUrl)
              .build()
              .post()
              .uri("/api/calculations/mtm")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
              .body(request)
              .retrieve()
              .body(MtmCalculationResponse.class);
      if (response == null) {
        throw SetupException.unavailable("Valuation service returned an empty MTM response");
      }
      return response;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
        LOGGER.warn(
            "Valuation MTM rejected for contract {} (request {}) with HTTP 422: {}",
            request.getTransactionReference(),
            request.getCalculationRequestReference(),
            boundedResponseBody(exception));
        throw SetupException.conflict("MTM inputs are not eligible for valuation");
      }
      throw SetupException.unavailable("Valuation service rejected or could not process MTM");
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Valuation service is unavailable");
    }
  }

  public LendingFeeCalculationResponse calculateLendingFee(
      LendingFeeCalculationRequest request, String bearerToken) {
    try {
      var response =
          builder
              .baseUrl(baseUrl)
              .build()
              .post()
              .uri("/api/calculations/lending-fee")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
              .body(request)
              .retrieve()
              .body(LendingFeeCalculationResponse.class);
      if (response == null) {
        throw SetupException.unavailable("Valuation service returned an empty fee result");
      }
      return response;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
        throw SetupException.conflict("Fee calculation inputs are not eligible");
      }
      throw SetupException.unavailable("Valuation service could not process fee calculation");
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Valuation service is unavailable");
    }
  }

  public RebateCalculationResponse calculateRebate(
      RebateCalculationRequest request, String bearerToken) {
    try {
      var response = builder.baseUrl(baseUrl).build().post()
          .uri("/api/calculations/rebate")
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
          .body(request)
          .retrieve()
          .body(RebateCalculationResponse.class);
      if (response == null) throw SetupException.unavailable("Valuation service returned an empty rebate result");
      return response;
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
        throw SetupException.conflict("Rebate calculation inputs are not eligible");
      }
      throw SetupException.unavailable("Valuation service could not process rebate calculation");
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Valuation service is unavailable");
    }
  }

  public List<RebateCalculationResponse> rebateResults(
      String transactionReference, String custodianOrganizationReference,
      LocalDate from, LocalDate to, String bearerToken) {
    try {
      List<RebateCalculationResponse> response = builder.baseUrl(baseUrl).build().get()
          .uri(uri -> uri.path("/api/calculations/rebate")
              .queryParam("transactionReference", transactionReference)
              .queryParam("custodianOrganizationReference", custodianOrganizationReference)
              .queryParam("from", from).queryParam("to", to).build())
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {});
      return response == null ? List.of() : response;
    } catch (RestClientResponseException exception) {
      throw SetupException.unavailable("Valuation service could not list rebate results");
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Valuation service is unavailable");
    }
  }

  public List<LendingFeeCalculationResponse> lendingFeeResults(
      String transactionReference,
      String custodianOrganizationReference,
      LocalDate from,
      LocalDate to,
      String bearerToken) {
    try {
      List<LendingFeeCalculationResponse> response =
          builder
              .baseUrl(baseUrl)
              .build()
              .get()
              .uri(uri -> uri.path("/api/calculations/lending-fee")
                  .queryParam("transactionReference", transactionReference)
                  .queryParam("custodianOrganizationReference", custodianOrganizationReference)
                  .queryParam("from", from)
                  .queryParam("to", to)
                  .build())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
              .retrieve()
              .body(new ParameterizedTypeReference<>() {});
      return response == null ? List.of() : response;
    } catch (RestClientResponseException exception) {
      throw SetupException.unavailable("Valuation service could not list fee results");
    } catch (RestClientException exception) {
      throw SetupException.unavailable("Valuation service is unavailable");
    }
  }

  private String boundedResponseBody(RestClientResponseException exception) {
    String responseBody = exception.getResponseBodyAsString().replaceAll("\\s+", " ");
    return responseBody.length() > MAX_ERROR_BODY_LENGTH
        ? responseBody.substring(0, MAX_ERROR_BODY_LENGTH)
        : responseBody;
  }
}
