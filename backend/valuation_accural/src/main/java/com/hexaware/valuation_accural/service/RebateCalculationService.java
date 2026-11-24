package com.hexaware.valuation_accural.service;

import com.hexaware.valuation_accural.calculator.DailyCashCollateralRebateCalculator;
import com.hexaware.valuation_accural.config.LoanCalculationProperties;
import com.hexaware.valuation_accural.dto.request.RebateCalculationRequest;
import com.hexaware.valuation_accural.dto.response.RebateCalculationResponse;
import com.hexaware.valuation_accural.entity.CalculationResult;
import com.hexaware.valuation_accural.entity.CalculationRun;
import com.hexaware.valuation_accural.exception.CalculationException;
import com.hexaware.valuation_accural.repository.CalculationResultRepository;
import com.hexaware.valuation_accural.repository.CalculationRunRepository;
import com.hexaware.valuation_accural.security.authorization.CalculationAuthorization;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
public class RebateCalculationService {
  private static final String REQUESTER = "CUSTODIAN";
  private static final String RESULT_TYPE = "REBATE";
  private static final String RULE_VERSION = "CASH-COLLATERAL-REBATE-ACT365-UTC-V1";

  private final CalculationAuthorization authorization;
  private final LoanCalculationProperties properties;
  private final DailyCashCollateralRebateCalculator calculator;
  private final Clock clock;
  private final CalculationRunRepository runs;
  private final CalculationResultRepository results;
  private final ObjectMapper mapper;
  private final PlatformTransactionManager transactionManager;

  public RebateCalculationResponse calculate(
      Authentication authentication, RebateCalculationRequest request) {
    authorization.requireAuthoritative(authentication, request.getCustodianOrganizationReference());
    validate(request);
    String requestHash = hash(inputSnapshot(request));
    var existing = runs.findByRequesterServiceAndRequestReference(
        REQUESTER, request.getCalculationRequestReference());
    if (existing.isPresent()) {
      if (!requestHash.equals(existing.get().getRequestPayloadHash())) {
        throw invalid("Calculation request reference was reused with different inputs");
      }
      if ("COMPLETED".equals(existing.get().getStatus())) return existingResult(existing.get());
    }

    BigDecimal amount = calculator.calculate(
        request.getSettledCashCollateral(), request.getAnnualRebatePercentage());
    Instant calculatedAt = clock.instant();
    RebateCalculationResponse response = RebateCalculationResponse.builder()
        .status("COMPLETED")
        .resultReference(UUID.randomUUID().toString())
        .calculationRequestReference(request.getCalculationRequestReference())
        .transactionReference(request.getTransactionReference())
        .accrualDate(request.getAccrualDate())
        .cutoff(request.getCutoff())
        .settledCashCollateral(request.getSettledCashCollateral())
        .annualRebatePercentage(request.getAnnualRebatePercentage())
        .amount(amount)
        .currency(request.getCurrency())
        .dayCountBasis(request.getDayCountBasis())
        .rebateBasis("DAILY_SETTLED_CASH_COLLATERAL")
        .inputVersion(request.getInputVersion())
        .calculationRuleVersion(RULE_VERSION)
        .supersedesResultReference(request.getSupersedesResultReference())
        .calculatedAt(calculatedAt)
        .build();

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    try {
      RebateCalculationResponse saved = transaction.execute(status -> {
        var concurrent = runs.findByRequesterServiceAndRequestReference(
            REQUESTER, request.getCalculationRequestReference());
        if (concurrent.isPresent() && "COMPLETED".equals(concurrent.get().getStatus())) {
          if (!requestHash.equals(concurrent.get().getRequestPayloadHash())) {
            throw invalid("Calculation request reference was reused with different inputs");
          }
          return existingResult(concurrent.get());
        }
        Instant createdAt = clock.instant();
        Map<String, Object> inputs = inputSnapshot(request);
        CalculationRun run = concurrent.orElseGet(() -> CalculationRun.builder()
            .inputSchemaVersion(1)
            .inputs(inputs)
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
            .attemptCount(0)
            .createdAt(createdAt)
            .build());
        run.setInputs(inputs);
        run.setPriceSnapshots(Map.of("settledCashCollateral", request.getSettledCashCollateral().toPlainString()));
        run.setRuleParameters(ruleParameters());
        run.setStatus("COMPLETED");
        run.setErrorMessage(null);
        run.setAttemptCount(run.getAttemptCount() == null ? 1 : run.getAttemptCount() + 1);
        run.setCompletedAt(calculatedAt);
        run = runs.saveAndFlush(run);

        Map<String, Object> details = mapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
        CalculationResult result = results.save(CalculationResult.builder()
            .resultReference(response.getResultReference())
            .runId(run.getRunId())
            .itemKey("REBATE:" + request.getAccrualDate())
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
        result.setDetails(mapper.convertValue(response, new TypeReference<Map<String, Object>>() {}));
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
      throw invalid("Concurrent rebate calculation conflicted with another result");
    }
  }

  public java.util.List<RebateCalculationResponse> list(
      Authentication authentication,
      String transactionReference,
      String custodianOrganizationReference,
      LocalDate from,
      LocalDate to) {
    authorization.requireAuthoritative(authentication, custodianOrganizationReference);
    if (transactionReference == null || transactionReference.isBlank()
        || from == null || to == null || from.isAfter(to) || to.isAfter(LocalDate.now(clock))) {
      throw invalid("Rebate calculation date range or contract reference is invalid");
    }
    return results
        .findByTransactionReferenceAndResultTypeAndCalculationDateBetweenOrderByCalculationDateAscCreatedAtAsc(
            transactionReference, RESULT_TYPE, from, to)
        .stream()
        .filter(result -> belongsToCustodian(result, custodianOrganizationReference))
        .map(result -> mapper.convertValue(result.getDetails(), RebateCalculationResponse.class))
        .toList();
  }

  private boolean belongsToCustodian(CalculationResult result, String custodianReference) {
    CalculationRun run = runs.findById(result.getRunId())
        .orElseThrow(() -> new IllegalStateException("Calculation result has no run"));
    return custodianReference.equals(run.getInputs().get("custodianOrganizationReference"));
  }

  private RebateCalculationResponse existingResult(CalculationRun run) {
    CalculationResult result = results.findByRunIdAndItemKey(
            run.getRunId(), "REBATE:" + run.getPeriodStart())
        .orElseThrow(() -> new IllegalStateException("Completed rebate run has no result"));
    return mapper.convertValue(result.getDetails(), RebateCalculationResponse.class);
  }

  private void validate(RebateCalculationRequest request) {
    if (request.getAccrualDate() == null || request.getCutoff() == null
        || request.getSettledCashCollateral() == null || request.getAnnualRebatePercentage() == null
        || request.getCurrency() == null || request.getInputVersion() == null
        || request.getCalculationRequestReference() == null
        || request.getTransactionReference() == null
        || request.getBorrowerOrganizationReference() == null
        || request.getCustodianOrganizationReference() == null) {
      throw invalid("Rebate calculation inputs are incomplete");
    }
    Instant expectedCutoff = request.getAccrualDate().plusDays(1)
        .atStartOfDay(ZoneOffset.UTC).toInstant();
    int scale;
    try {
      scale = Currency.getInstance(request.getCurrency()).getDefaultFractionDigits();
    } catch (IllegalArgumentException exception) {
      throw invalid("Unsupported rebate currency");
    }
    if (!properties.getSupportedCurrencies().contains(request.getCurrency())
        || scale != 2
        || !"ACT_365".equals(request.getDayCountBasis())
        || !expectedCutoff.equals(request.getCutoff())
        || request.getCutoff().isAfter(clock.instant())
        || request.getSettledCashCollateral().signum() < 0
        || request.getSettledCashCollateral().scale() > 2
        || request.getAnnualRebatePercentage().signum() < 0
        || request.getAnnualRebatePercentage().scale() > 6
        || request.getInputVersion().length() > 128
        || (request.getSupersedesResultReference() != null
            && !request.getSupersedesResultReference().matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) {
      throw invalid("Unsupported rebate currency, amount, rate, cutoff or day-count basis");
    }
  }

  private Map<String, Object> inputSnapshot(RebateCalculationRequest request) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("calculationRequestReference", request.getCalculationRequestReference());
    values.put("transactionReference", request.getTransactionReference());
    values.put("accrualDate", request.getAccrualDate().toString());
    values.put("cutoff", request.getCutoff().toString());
    values.put("settledCashCollateral", request.getSettledCashCollateral().toPlainString());
    values.put("annualRebatePercentage", request.getAnnualRebatePercentage().toPlainString());
    values.put("currency", request.getCurrency());
    values.put("dayCountBasis", request.getDayCountBasis());
    values.put("inputVersion", request.getInputVersion());
    values.put("borrowerOrganizationReference", request.getBorrowerOrganizationReference());
    values.put("custodianOrganizationReference", request.getCustodianOrganizationReference());
    values.put("supersedesResultReference", request.getSupersedesResultReference());
    return values;
  }

  private Map<String, Object> ruleParameters() {
    return Map.of(
        "ruleVersion", RULE_VERSION,
        "rebateBasis", "DAILY_SETTLED_CASH_COLLATERAL",
        "timezone", "UTC",
        "dailyCutoff", "NEXT_DAY_START_EXCLUSIVE",
        "dayCountBasis", "ACT_365",
        "annualRateAsPercentage", true,
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
