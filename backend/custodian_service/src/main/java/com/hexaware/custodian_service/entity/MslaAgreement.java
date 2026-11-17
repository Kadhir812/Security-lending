package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.dto.request.EligibleSecurities;
import com.hexaware.custodian_service.enums.AgreementStatus;
import com.hexaware.custodian_service.enums.CollateralType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "msla_agreement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MslaAgreement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "msla_id")
  private Long mslaId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "msla_reference", nullable = false, unique = true, length = 80)
  private String mslaReference;

  @Column(name = "mandate_id", nullable = false)
  private Long mandateId;

  @Column(name = "borrower_org_id", nullable = false)
  private Long borrowerOrgId;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "expiry_date")
  private LocalDate expiryDate;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "agreement_status",
      nullable = false,
      columnDefinition = "enum('DRAFT','ACTIVE','INACTIVE','EXPIRED')")
  private AgreementStatus agreementStatus;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "base_currency", nullable = false, length = 3, columnDefinition = "char(3)")
  private String baseCurrency;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "eligible_securities", nullable = false, columnDefinition = "json")
  private EligibleSecurities eligibleSecurities;

  @Enumerated(EnumType.STRING)
  @Column(
      name = "collateral_type",
      nullable = false,
      columnDefinition = "enum('CASH','ETF','GOVT_BOND','CORPORATE_BOND','EQUITY')")
  private CollateralType collateralType;

  @Column(name = "collateral_coverage_pct", nullable = false, precision = 9, scale = 4)
  private BigDecimal collateralCoveragePct;

  @Column(name = "mtm_interval_minutes", nullable = false)
  private Integer mtmIntervalMinutes;

  @Column(name = "margin_call_window_hours", nullable = false)
  private Integer marginCallWindowHours;

  @Column(name = "recall_rule", length = 500)
  private String recallRule;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "default_rules", columnDefinition = "json")
  private Map<String, Object> defaultRules;

  @Column(name = "settlement_calendar_code", nullable = false, length = 80)
  private String settlementCalendarCode;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "created_at", columnDefinition = "datetime(6)")
  private Instant createdAt;

  @Column(name = "created_by_user_id")
  private Long createdByUserId;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "updated_at", columnDefinition = "datetime(6)")
  private Instant updatedAt;

  @Column(name = "updated_by_user_id")
  private Long updatedByUserId;
}
