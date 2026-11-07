package com.hexaware.custodian_service.dto.response;
import com.hexaware.custodian_service.dto.request.EligibleSecurities;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Set;
import java.util.Map;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.AssetType;
import com.hexaware.custodian_service.enums.CollateralType;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MslaResponse {
 private String mslaReference; private String mandateReference; private String lenderOrganizationReference; private String borrowerOrganizationReference; private String custodianOrganizationReference;
 private LocalDate effectiveDate; private LocalDate expiryDate; private AgreementStatus agreementStatus; private boolean currentlyEffective;
 private String baseCurrency; private EligibleSecurities eligibleSecurities; private CollateralType collateralType; private BigDecimal collateralCoveragePct; private Integer mtmIntervalMinutes; private Integer marginCallWindowHours; private String recallRule; private Map<String,Object> defaultRules; private String settlementCalendarCode;
 private Long version; private Instant createdAt; private Long createdByUserId; private Instant updatedAt; private Long updatedByUserId;
}
