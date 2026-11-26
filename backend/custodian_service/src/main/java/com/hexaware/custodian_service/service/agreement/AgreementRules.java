package com.hexaware.custodian_service.service.agreement;

import com.hexaware.custodian_service.dto.request.MslaRequest;
import com.hexaware.custodian_service.entity.LenderMandate;
import com.hexaware.custodian_service.entity.MslaAgreement;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.CollateralType;
import com.hexaware.custodian_service.enums.MandateStatus;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.service.setup.SetupValidation;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class AgreementRules {

  private final ObjectMapper json;

  public void validate(MslaRequest request) {
    SetupValidation.dates(request.getEffectiveDate(), request.getExpiryDate());
    validateEligibleSecurities(request);
    validateCollateral(request);
    validateDefaultRules(request);
  }

  private void validateEligibleSecurities(MslaRequest request) {
    var eligibility = request.getEligibleSecurities();
    if (eligibility == null
        || eligibility.getAllowedTypes() == null
        || eligibility.getAllowedTypes().isEmpty()
        || eligibility.getAllowedTypes().stream().anyMatch(type -> type == null)
        || eligibility.getAllowedTypes().stream().distinct().count()
            != eligibility.getAllowedTypes().size()) {
      throw SetupException.invalid(
          "eligibleSecurities.allowed_types must be nonempty, valid and duplicate-free");
    }
  }

  private void validateCollateral(MslaRequest request) {
    if (request.getCollateralType() != CollateralType.CASH) {
      throw SetupException.invalid(
          "Only CASH collateral is supported in this phase; effective CASH haircut is zero");
    }
  }

  private void validateDefaultRules(MslaRequest request) {
    if (request.getDefaultRules() == null) {
      return;
    }
    if (json.writeValueAsString(request.getDefaultRules()).length() > 16384) {
      throw SetupException.invalid("defaultRules exceeds 16 KiB");
    }
    validateJson(request.getDefaultRules(), 0);
  }

  private void validateJson(Object value, int depth) {
    if (depth > 8) {
      throw SetupException.invalid("defaultRules exceeds maximum nesting depth");
    }
    if (value instanceof Map<?, ?> map) {
      for (var entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)
            || key.isBlank()
            || key.length() > 100) {
          throw SetupException.invalid("Invalid defaultRules key");
        }
        validateJson(entry.getValue(), depth + 1);
      }
    } else if (value instanceof Collection<?> values) {
      for (Object item : values) {
        validateJson(item, depth + 1);
      }
    } else if (value != null
        && !(value instanceof String || value instanceof Boolean || value instanceof Number)) {
      throw SetupException.invalid("defaultRules must contain JSON values");
    }
  }

  public void transition(
      AgreementStatus oldStatus, AgreementStatus newStatus, LocalDate expiry, LocalDate today) {
    if (oldStatus == newStatus) {
      return;
    }

    boolean allowed =
        switch (oldStatus) {
          case DRAFT ->
              newStatus == AgreementStatus.ACTIVE
                  || newStatus == AgreementStatus.INACTIVE
                  || newStatus == AgreementStatus.EXPIRED;
          case ACTIVE ->
              newStatus == AgreementStatus.INACTIVE || newStatus == AgreementStatus.EXPIRED;
          case INACTIVE ->
              newStatus == AgreementStatus.ACTIVE || newStatus == AgreementStatus.EXPIRED;
          case EXPIRED -> false;
        };
    if (!allowed) {
      throw SetupException.conflict("This agreement status transition is not allowed");
    }
    if (newStatus == AgreementStatus.EXPIRED
        && (expiry == null || !expiry.isBefore(today))) {
      throw SetupException.conflict("EXPIRED requires an expiry date before today");
    }
  }

  public void activate(MslaAgreement agreement, LenderMandate mandate, LocalDate today) {
    boolean mandateActive =
        mandate.getStatus() == MandateStatus.ACTIVE
            && SetupValidation.covers(
                mandate.getEffectiveFrom(), mandate.getEffectiveTo(), today);
    if (!mandateActive) {
      throw SetupException.conflict("Activation requires a currently active mandate");
    }
    if (agreement.getExpiryDate() != null && agreement.getExpiryDate().isBefore(today)) {
      throw SetupException.conflict("An expired agreement cannot be activated");
    }

    boolean agreementFitsMandate =
        !agreement.getEffectiveDate().isBefore(mandate.getEffectiveFrom())
            && (mandate.getEffectiveTo() == null
                || (agreement.getExpiryDate() != null
                    && !agreement.getExpiryDate().isAfter(mandate.getEffectiveTo())));
    if (!agreementFitsMandate) {
      throw SetupException.conflict("Agreement dates must fit the mandate period");
    }
    if (agreement.getDefaultRules() != null && !agreement.getDefaultRules().isEmpty()) {
      throw SetupException.conflict(
          "default_rules business semantics are not defined; retain this agreement as DRAFT "
              + "until its rules are approved");
    }
  }
}
