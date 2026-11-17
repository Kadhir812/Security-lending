package com.hexaware.valuation_accural.service;

import com.hexaware.valuation_accural.calculator.DailyLendingFeeCalculator;
import com.hexaware.valuation_accural.client.MarketDataClient;
import com.hexaware.valuation_accural.config.LoanCalculationProperties;
import com.hexaware.valuation_accural.dto.request.LendingFeeCalculationRequest;
import com.hexaware.valuation_accural.dto.response.LendingFeeCalculationResponse;
import com.hexaware.valuation_accural.entity.CalculationResult;
import com.hexaware.valuation_accural.entity.CalculationRun;
import com.hexaware.valuation_accural.exception.CalculationException;
import com.hexaware.valuation_accural.repository.CalculationResultRepository;
import com.hexaware.valuation_accural.repository.CalculationRunRepository;
import com.hexaware.valuation_accural.security.authorization.CalculationAuthorization;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class LendingFeeCalculationService {
  private static final String RULE_VERSION = "LENDING-FEE-MARKET-ACT365-UTC-V1";
  private static final String RESULT_TYPE = "LENDING_FEE";
  private static final String REQUESTER = "CUSTODIAN";

  private final CalculationAuthorization authorization;
  private final MarketDataClient marketData;
  private final LoanCalculationProperties properties;
  private final DailyLendingFeeCalculator calculator;
  private final Clock clock;
  private final CalculationRunRepository runs;
  private final CalculationResultRepository results;
  private final ObjectMapper mapper;
  private final PlatformTransactionManager transactionManager;

  public LendingFeeCalculationResponse calculate(
      Authentication authentication, LendingFeeCalculationRequest request) {
    authorization.requireAuthoritative(
        authentication, request.getCustodianOrganizationReference());
    validate(request);

    String requestHash = hash(inputSnapshot(request));
    var existing =
        runs.findByRequesterServiceAndRequestReference(
            REQUESTER, request.getCalculationRequestReference());
    if (existing.isPresent()) {
      if (!requestHash.equals(existing.get().getRequestPayloadHash())) {
        throw invalid("Calculation request reference was reused with different inputs");
      }
      if ("COMPLETED".equals(existing.get().getStatus())) {
        return existingResult(existing.get());
      }
    }

    var security = marketData.security(request.getSecurityIsin());
    if (!request.getSecurityIsin().equals(security.getIsin())
        || !"ACTIVE".equals(security.getStatus())
        || !request.getCurrency().equals(security.getCurrency())) {
      throw invalid("Security status or currency is not eligible for fee valuation");
    }
    if (!"EQUITY".equals(security.getSecurityType())
        && !"ETF".equals(security.getSecurityType())) {
      throw invalid("Unsupported security price convention for fee valuation");
    }

    MarketDataClient.PriceReference price;
    try {
      price = marketData.priceAtOrBefore(request.getSecurityIsin(), request.getCutoff());
    } catch (CalculationException exception) {
      if (exception.getStatus() != HttpStatus.SERVICE_UNAVAILABLE) {
        throw exception;
      }
      persistPending(request, requestHash, exception.getMessage());
      return pending(request, exception.getMessage());
    }
    Instant now = clock.instant();
    Duration allowedAge = properties.getMaxPriceAge().compareTo(Duration.ofHours(24)) > 0
        ? Duration.ofHours(24)
        : properties.getMaxPriceAge();
    if (!request.getSecurityIsin().equals(price.getIsin())
        || !request.getCurrency().equals(price.getCurrency())
        || price.getMarketPrice() == null
        || price.getMarketPrice().signum() <= 0
        || price.getPriceReference() == null
        || price.getPriceReference().isBlank()
        || price.getSourceName() == null
        || price.getSourceName().isBlank()
        || price.getPriceTimestamp() == null
        || price.getPriceTimestamp().isAfter(request.getCutoff())
        || Duration.between(price.getPriceTimestamp(), request.getCutoff())
            .compareTo(allowedAge) > 0
        || price.isStale()) {
      String reason = "No valid market price exists at the UTC accrual cutoff";
      persistPending(request, requestHash, reason);
      return pending(request, reason);
    }

    BigDecimal amount = calculator.calculate(
        price.getMarketPrice(),
        request.getSettledOutstandingQuantity(),
        request.getAnnualFeePercentage());
    Instant calculatedAt = clock.instant();
    LendingFeeCalculationResponse response = LendingFeeCalculationResponse.builder()
        .status("COMPLETED")
        .resultReference(UUID.randomUUID().toString())
        .calculationRequestReference(request.getCalculationRequestReference())
        .transactionReference(request.getTransactionReference())
        .accrualDate(request.getAccrualDate())
        .cutoff(request.getCutoff())
        .settledOutstandingQuantity(request.getSettledOutstandingQuantity())
        .marketPrice(price.getMarketPrice())
        .priceReference(price.getPriceReference())
        .priceSource(price.getSourceName())
        .priceTimestamp(price.getPriceTimestamp())
        .annualFeePercentage(request.getAnnualFeePercentage())
        .amount(amount)
        .currency(request.getCurrency())
        .dayCountBasis(request.getDayCountBasis())
        .feeBasis("DAILY_MARKET_VALUE")
        .inputVersion(request.getInputVersion())
        .calculationRuleVersion(RULE_VERSION)
        .supersedesResultReference(request.getSupersedesResultReference())
        .calculatedAt(calculatedAt)
        .build();

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    try {
      LendingFeeCalculationResponse saved = transaction.execute(status -> {
        var concurrent = runs.findByRequesterServiceAndRequestReference(
            REQUESTER, request.getCalculationRequestReference());
        if (concurrent.isPresent() && "COMPLETED".equals(concurrent.get().getStatus())) {
          if (!requestHash.equals(concurrent.get().getRequestPayloadHash())) {
            throw invalid("Calculation request reference was reused with different inputs");
          }
          return existingResult(concurrent.get());
        }
        Instant createdAt = clock.instant();
        CalculationRun run = concurrent.orElseGet(() -> CalculationRun.builder()
            .inputSchemaVersion(1)
            .inputs(inputSnapshot(request))
            .priceSnapshots(new LinkedHashMap<>())
            .ruleParameters(ruleParameters())
            .implementationDigest(hashText(RULE_VERSION))
            .runReference(UUID.randomUUID().toString())
            .requesterService(REQUESTER)
            .requestReference(request.getCalculationRequestReference())
            .requestPayloadHash(requestHash)
            .correlationReference(request.getTransactionReference())
            .calculationType(RESULT_TYPE)
            .purpose("AUTHORITATIVE")
            .transactionReference(request.getTransactionReference())
            .borrowerOrganizationReference(request.getBorrowerOrganizationReference())
            .inputContractVersion(0L)
            .valuationAt(request.getCutoff())
            .periodStart(request.getAccrualDate())
            .periodEnd(request.getAccrualDate())
            .currency(request.getCurrency())
            .ruleVersion(RULE_VERSION)
            .status("QUEUED")
            .attemptCount(0)
            .createdAt(createdAt)
            .build());
        run.setInputs(inputSnapshot(request));
        run.setPriceSnapshots(priceSnapshot(price));
        run.setRuleParameters(ruleParameters());
        run.setStatus("COMPLETED");
        run.setErrorMessage(null);
        run.setAttemptCount(run.getAttemptCount() == null ? 1 : run.getAttemptCount() + 1);
        run.setCompletedAt(calculatedAt);
        run.setValuationAt(request.getCutoff());
        run.setPeriodStart(request.getAccrualDate());
        run.setPeriodEnd(request.getAccrualDate());
        run = runs.saveAndFlush(run);

        Map<String, Object> details =
            mapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
        CalculationResult result = results.save(CalculationResult.builder()
            .resultReference(response.getResultReference())
            .runId(run.getRunId())
            .itemKey("LENDING_FEE:" + request.getAccrualDate())
            .resultType(RESULT_TYPE)
            .transactionReference(request.getTransactionReference())
            .calculationDate(request.getAccrualDate())
            .currency(request.getCurrency())
            .amount(amount)
            .resultSchemaVersion(1)
            .details(details)
            .supersedesResultReference(request.getSupersedesResultReference())
            .createdAt(createdAt)
            .build());
        response.setResultReference(result.getResultReference());
        Map<String, Object> persistedDetails =
            mapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
        result.setDetails(persistedDetails);
        results.save(result);
        return response;
      });
      return saved;
    } catch (DataIntegrityViolationException duplicate) {
      var concurrent = runs.findByRequesterServiceAndRequestReference(
          REQUESTER, request.getCalculationRequestReference());
      if (concurrent.isPresent() && requestHash.equals(concurrent.get().getRequestPayloadHash())) {
        return existingResult(concurrent.get());
      }
      throw invalid("Concurrent fee calculation conflicted with another result");
    }
  }

  public List<LendingFeeCalculationResponse> list(
      Authentication authentication,
      String transactionReference,
      String custodianOrganizationReference,
      LocalDate from,
      LocalDate to) {
    authorization.requireAuthoritative(authentication, custodianOrganizationReference);
    if (from == null || to == null || from.isAfter(to) || to.isAfter(LocalDate.now(clock))) {
      throw invalid("Accrual date range is invalid");
    }
    return results
        .findByTransactionReferenceAndResultTypeAndCalculationDateBetweenOrderByCalculationDateAscCreatedAtAsc(
            transactionReference, RESULT_TYPE, from, to)
        .stream()
        .filter(result -> belongsToCustodian(result, custodianOrganizationReference))
        .map(result -> mapper.convertValue(
            result.getDetails(), LendingFeeCalculationResponse.class))
        .toList();
  }

  private boolean belongsToCustodian(
      CalculationResult result, String custodianOrganizationReference) {
    CalculationRun run = runs.findById(result.getRunId())
        .orElseThrow(() -> new IllegalStateException("Calculation result has no run"));
    return custodianOrganizationReference.equals(
        run.getInputs().get("custodianOrganizationReference"));
  }

  private void persistPending(
      LendingFeeCalculationRequest request, String requestHash, String reason) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(status -> {
      var existing = runs.findByRequesterServiceAndRequestReference(
          REQUESTER, request.getCalculationRequestReference());
      if (existing.isPresent()) {
        if (!requestHash.equals(existing.get().getRequestPayloadHash())) {
          throw invalid("Calculation request reference was reused with different inputs");
        }
        if ("COMPLETED".equals(existing.get().getStatus())) return;
        CalculationRun run = existing.get();
        run.setStatus("QUEUED");
        run.setErrorMessage(reason);
        run.setAttemptCount(run.getAttemptCount() + 1);
        runs.save(run);
        return;
      }
      Instant now = clock.instant();
      runs.save(CalculationRun.builder()
          .inputSchemaVersion(1)
          .inputs(inputSnapshot(request))
          .priceSnapshots(Map.of())
          .ruleParameters(ruleParameters())
          .implementationDigest(hashText(RULE_VERSION))
          .runReference(UUID.randomUUID().toString())
          .requesterService(REQUESTER)
          .requestReference(request.getCalculationRequestReference())
          .requestPayloadHash(requestHash)
          .correlationReference(request.getTransactionReference())
          .calculationType(RESULT_TYPE)
          .purpose("AUTHORITATIVE")
          .transactionReference(request.getTransactionReference())
          .borrowerOrganizationReference(request.getBorrowerOrganizationReference())
          .inputContractVersion(0L)
          .valuationAt(request.getCutoff())
          .periodStart(request.getAccrualDate())
          .periodEnd(request.getAccrualDate())
          .currency(request.getCurrency())
          .ruleVersion(RULE_VERSION)
          .status("QUEUED")
          .attemptCount(1)
          .errorMessage(reason)
          .createdAt(now)
          .build());
    });
  }

  private LendingFeeCalculationResponse pending(
      LendingFeeCalculationRequest request, String reason) {
    return LendingFeeCalculationResponse.builder()
        .status("PENDING")
        .calculationRequestReference(request.getCalculationRequestReference())
        .transactionReference(request.getTransactionReference())
        .accrualDate(request.getAccrualDate())
        .cutoff(request.getCutoff())
        .settledOutstandingQuantity(request.getSettledOutstandingQuantity())
        .annualFeePercentage(request.getAnnualFeePercentage())
        .currency(request.getCurrency())
        .dayCountBasis(request.getDayCountBasis())
        .feeBasis("DAILY_MARKET_VALUE")
        .inputVersion(request.getInputVersion())
        .calculationRuleVersion(RULE_VERSION)
        .pendingReason(reason)
        .build();
  }

  private LendingFeeCalculationResponse existingResult(CalculationRun run) {
    CalculationResult result = results.findByRunIdAndItemKey(
            run.getRunId(), "LENDING_FEE:" + run.getPeriodStart())
        .orElseThrow(() -> new IllegalStateException("Completed fee run has no result"));
    return mapper.convertValue(result.getDetails(), LendingFeeCalculationResponse.class);
  }

  private void validate(LendingFeeCalculationRequest request) {
    Instant expectedCutoff = request.getAccrualDate().plusDays(1)
        .atStartOfDay(ZoneOffset.UTC).toInstant();
    int scale;
    try {
      scale = Currency.getInstance(request.getCurrency()).getDefaultFractionDigits();
    } catch (IllegalArgumentException exception) {
      throw invalid("Unsupported fee currency");
    }
    if (!properties.getSupportedCurrencies().contains(request.getCurrency())
        || scale != 2
        || !"ACT_365".equals(request.getDayCountBasis())
        || !expectedCutoff.equals(request.getCutoff())
        || request.getCutoff().isAfter(clock.instant())
        || request.getInputVersion().length() > 128) {
      throw invalid("Unsupported fee currency, day-count basis, cutoff or input version");
    }
  }

  private Map<String, Object> inputSnapshot(LendingFeeCalculationRequest request) {
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("calculationRequestReference", request.getCalculationRequestReference());
    inputs.put("transactionReference", request.getTransactionReference());
    inputs.put("securityIsin", request.getSecurityIsin());
    inputs.put("accrualDate", request.getAccrualDate().toString());
    inputs.put("cutoff", request.getCutoff().toString());
    inputs.put("settledOutstandingQuantity", request.getSettledOutstandingQuantity());
    inputs.put("annualFeePercentage", request.getAnnualFeePercentage().toPlainString());
    inputs.put("currency", request.getCurrency());
    inputs.put("dayCountBasis", request.getDayCountBasis());
    inputs.put("inputVersion", request.getInputVersion());
    inputs.put("borrowerOrganizationReference", request.getBorrowerOrganizationReference());
    inputs.put("custodianOrganizationReference", request.getCustodianOrganizationReference());
    inputs.put("supersedesResultReference", request.getSupersedesResultReference());
    return inputs;
  }

  private Map<String, Object> priceSnapshot(MarketDataClient.PriceReference price) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("priceReference", price.getPriceReference());
    snapshot.put("priceSource", price.getSourceName());
    snapshot.put("priceTimestamp", price.getPriceTimestamp().toString());
    snapshot.put("marketPrice", price.getMarketPrice().toPlainString());
    snapshot.put("currency", price.getCurrency());
    return snapshot;
  }

  private Map<String, Object> ruleParameters() {
    return Map.of(
        "ruleVersion", RULE_VERSION,
        "feeBasis", "DAILY_MARKET_VALUE",
        "timezone", "UTC",
        "dailyCutoff", "NEXT_DAY_START_EXCLUSIVE",
        "dayCountBasis", "ACT_365",
        "annualRateAsPercentage", true,
        "intermediateMonetaryRounding", "NONE",
        "dailyRounding", "HALF_UP_2");
  }

  private String hash(Map<String, Object> values) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(mapper.writeValueAsString(values).getBytes(StandardCharsets.UTF_8)));
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
}
