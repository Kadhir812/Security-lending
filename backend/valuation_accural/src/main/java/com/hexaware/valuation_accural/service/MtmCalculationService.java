package com.hexaware.valuation_accural.service;

import com.hexaware.valuation_accural.calculator.LoanApprovalCalculator;
import com.hexaware.valuation_accural.client.MarketDataClient;
import com.hexaware.valuation_accural.config.LoanCalculationProperties;
import com.hexaware.valuation_accural.dto.request.MtmCalculationRequest;
import com.hexaware.valuation_accural.dto.response.MtmCalculationResponse;
import com.hexaware.valuation_accural.entity.CalculationResult;
import com.hexaware.valuation_accural.entity.CalculationRun;
import com.hexaware.valuation_accural.exception.CalculationException;
import com.hexaware.valuation_accural.repository.CalculationResultRepository;
import com.hexaware.valuation_accural.repository.CalculationRunRepository;
import com.hexaware.valuation_accural.security.authorization.CalculationAuthorization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MtmCalculationService {
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  private final CalculationAuthorization authorization;
  private final MarketDataClient marketData;
  private final LoanCalculationProperties properties;
  private final LoanApprovalCalculator calculator;
  private final Clock clock;
  private final CalculationRunRepository runs;
  private final CalculationResultRepository results;
  private final ObjectMapper mapper;
  private final PlatformTransactionManager transactionManager;

  public MtmCalculationResponse calculate(
      org.springframework.security.core.Authentication authentication,
      MtmCalculationRequest request) {
    authorization.requireAuthoritative(
        authentication, request.getCustodianOrganizationReference());
    validate(request);

    String requestHash = hash(request);
    var existing =
        runs.findByRequesterServiceAndRequestReference(
            "CUSTODIAN", request.getCalculationRequestReference());
    if (existing.isPresent()) {
      return existingResult(existing.get(), requestHash);
    }

    var security = marketData.security(request.getSecurityIsin());
    if (!request.getSecurityIsin().equals(security.getIsin())
        || !"ACTIVE".equals(security.getStatus())
        || !request.getCurrency().equals(security.getCurrency())) {
      throw invalid("Security status or currency is not eligible");
    }
    if (!"EQUITY".equals(security.getSecurityType())
        && !"ETF".equals(security.getSecurityType())) {
      throw invalid("Unsupported security quote convention");
    }

    var price = marketData.latestPrice(request.getSecurityIsin());
    Instant now = clock.instant();
    if (!request.getSecurityIsin().equals(price.getIsin())
        || !request.getCurrency().equals(price.getCurrency())
        || price.getMarketPrice() == null
        || price.getMarketPrice().signum() <= 0
        || blank(price.getPriceReference())
        || blank(price.getSourceName())
        || price.getPriceTimestamp() == null
        || price.getPriceTimestamp().isAfter(now)
        || Duration.between(price.getPriceTimestamp(), now).compareTo(properties.getMaxPriceAge()) > 0
        || price.isStale()) {
      throw invalid("Market price is missing, mismatched, future-dated, or stale");
    }

    var amounts = calculator.calculate(
        price.getMarketPrice(), request.getOutstandingQuantity(),
        request.getCoveragePercentage());
    BigDecimal shortfall = calculator.shortfall(
        amounts.requiredCashCollateral(), request.getConfirmedCashCollateral());
    MtmCalculationResponse response = MtmCalculationResponse.builder()
        .calculationReference(UUID.randomUUID().toString())
        .calculationRequestReference(request.getCalculationRequestReference())
        .transactionReference(request.getTransactionReference())
        .securityIsin(request.getSecurityIsin())
        .outstandingQuantity(request.getOutstandingQuantity())
        .marketPrice(price.getMarketPrice())
        .currency(request.getCurrency())
        .priceReference(price.getPriceReference())
        .priceSource(price.getSourceName())
        .priceTimestamp(price.getPriceTimestamp())
        .calculatedAt(now)
        .loanMarketValue(amounts.loanMarketValue())
        .requiredCashCollateral(amounts.requiredCashCollateral())
        .confirmedCashCollateral(request.getConfirmedCashCollateral())
        .shortfallAmount(shortfall)
        .coveragePercentage(request.getCoveragePercentage())
        .cashHaircutPercentage(BigDecimal.ZERO.setScale(4))
        .calculationRuleVersion("MTM-CASH-V1")
        .inputAsOf(request.getInputAsOf())
        .inputVersion(request.getInputVersion())
        .custodianOrganizationReference(request.getCustodianOrganizationReference())
        .borrowerOrganizationReference(request.getBorrowerOrganizationReference())
        .build();

    Map<String, Object> inputs = inputSnapshot(request);
    Map<String, Object> priceSnapshot = new LinkedHashMap<>();
    priceSnapshot.put("securityType", security.getSecurityType());
    priceSnapshot.put("priceReference", price.getPriceReference());
    priceSnapshot.put("priceSource", price.getSourceName());
    priceSnapshot.put("price", price.getMarketPrice().toPlainString());
    priceSnapshot.put("currency", price.getCurrency());
    priceSnapshot.put("priceTimestamp", price.getPriceTimestamp().toString());
    Map<String, Object> ruleParameters = Map.of(
        "ruleVersion", "MTM-CASH-V1",
        "loanValueRounding", "HALF_UP_2",
        "requiredCollateralRounding", "CEILING_2",
        "cashHaircutPercentage", "0");

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    return transaction.execute(status -> {
      var concurrent =
          runs.findByRequesterServiceAndRequestReference(
              "CUSTODIAN", request.getCalculationRequestReference());
      if (concurrent.isPresent()) return existingResult(concurrent.get(), requestHash);

      Instant createdAt = clock.instant();
      String runReference = UUID.randomUUID().toString();
      CalculationRun run = runs.save(CalculationRun.builder()
          .inputSchemaVersion(1)
          .inputs(inputs)
          .priceSnapshots(priceSnapshot)
          .ruleParameters(ruleParameters)
          .implementationDigest(hashText("MTM-CASH-V1"))
          .runReference(runReference)
          .requesterService("CUSTODIAN")
          .requestReference(request.getCalculationRequestReference())
          .requestPayloadHash(requestHash)
          .correlationReference(request.getTransactionReference())
          .calculationType("MTM")
          .purpose("AUTHORITATIVE")
          .transactionReference(request.getTransactionReference())
          .borrowerOrganizationReference(request.getBorrowerOrganizationReference())
          .inputContractVersion(request.getInputVersion())
          .valuationAt(response.getCalculatedAt())
          .currency(request.getCurrency())
          .ruleVersion(response.getCalculationRuleVersion())
          .status("COMPLETED")
          .attemptCount(1)
          .createdAt(createdAt)
          .completedAt(response.getCalculatedAt())
          .build());
      Map<String, Object> details =
          mapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
      CalculationResult result = results.save(CalculationResult.builder()
          .resultReference(response.getCalculationReference())
          .runId(run.getRunId())
          .itemKey(request.getTransactionReference())
          .resultType("MTM")
          .transactionReference(request.getTransactionReference())
          .calculationDate(response.getCalculatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate())
          .currency(request.getCurrency())
          .amount(response.getMarketPrice())
          .loanValue(response.getLoanMarketValue())
          .requiredCollateral(response.getRequiredCashCollateral())
          .adjustedCollateral(response.getConfirmedCashCollateral())
          .coveragePct(response.getCoveragePercentage())
          .resultSchemaVersion(1)
          .details(details)
          .createdAt(createdAt)
          .build());
      response.setCalculationReference(result.getResultReference());
      return response;
    });
  }

  private void validate(MtmCalculationRequest request) {
    if (!properties.getSupportedCurrencies().contains(request.getCurrency())
        || request.getConfirmedCashCollateral() == null
        || request.getConfirmedCashCollateral().signum() < 0
        || request.getCoveragePercentage() == null
        || request.getCoveragePercentage().compareTo(ONE_HUNDRED) < 0
        || request.getInputAsOf() == null
        || request.getInputAsOf().isAfter(clock.instant())
        || request.getInputVersion() == null
        || request.getInputVersion() <= 0) {
      throw invalid("Unsupported or inconsistent MTM inputs");
    }
  }

  private MtmCalculationResponse existingResult(CalculationRun run, String requestHash) {
    if (!requestHash.equals(run.getRequestPayloadHash())) {
      throw invalid("Calculation request reference was reused with different inputs");
    }
    CalculationResult result = results.findByRunIdAndItemKey(run.getRunId(), run.getTransactionReference())
        .orElseThrow(() -> new IllegalStateException("Completed MTM run has no result"));
    return mapper.convertValue(result.getDetails(), MtmCalculationResponse.class);
  }

  private Map<String, Object> inputSnapshot(MtmCalculationRequest request) {
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("securityIsin", request.getSecurityIsin());
    inputs.put("outstandingQuantity", request.getOutstandingQuantity());
    inputs.put("coveragePercentage", request.getCoveragePercentage().toPlainString());
    inputs.put("confirmedCashCollateral", request.getConfirmedCashCollateral().toPlainString());
    inputs.put("currency", request.getCurrency());
    inputs.put("custodianOrganizationReference", request.getCustodianOrganizationReference());
    inputs.put("borrowerOrganizationReference", request.getBorrowerOrganizationReference());
    inputs.put("transactionReference", request.getTransactionReference());
    inputs.put("inputAsOf", request.getInputAsOf().toString());
    inputs.put("inputVersion", request.getInputVersion());
    return inputs;
  }

  private String hash(MtmCalculationRequest request) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(mapper.writeValueAsString(inputSnapshot(request))
              .getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the runtime", exception);
    }
  }

  private String hashText(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the runtime", exception);
    }
  }

  private CalculationException invalid(String message) {
    return new CalculationException(HttpStatus.UNPROCESSABLE_ENTITY, message);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
