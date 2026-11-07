package com.hexaware.custodian_service.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Queries existing workflow tables without adding duplicate ownership records. */
@Repository
@RequiredArgsConstructor
public class SetupUsageRepository {

  private static final long NO_EXCLUDED_ID = -1L;
  private static final LocalDate LAST_SQL_DATE = LocalDate.of(9999, 12, 31);

  private final JdbcTemplate jdbc;

  public boolean agreementUsed(Long agreementId) {
    Integer used =
        jdbc.queryForObject(
            """
            SELECT (
              EXISTS (SELECT 1 FROM locate_request WHERE msla_id = ?)
              OR EXISTS (SELECT 1 FROM loan_request WHERE msla_id = ?)
              OR EXISTS (SELECT 1 FROM counterparty_credit_limit WHERE msla_id = ?)
              OR EXISTS (SELECT 1 FROM collateral_haircut WHERE msla_id = ?)
              OR EXISTS (SELECT 1 FROM lending_fee WHERE msla_id = ?)
            )
            """,
            Integer.class,
            agreementId,
            agreementId,
            agreementId,
            agreementId,
            agreementId);
    return used != null && used != 0;
  }

  public boolean mandateReferenced(Long mandateId) {
    Long references =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM msla_agreement WHERE mandate_id = ?",
            Long.class,
            mandateId);
    return references != null && references > 0;
  }

  public boolean duplicateMandate(
      Long lenderId,
      Long custodianId,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      Long excludedMandateId) {
    LocalDate latestEnd = effectiveTo == null ? LAST_SQL_DATE : effectiveTo;
    Long duplicates =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM lender_custodian_mandate
            WHERE lender_org_id = ?
              AND custodian_org_id = ?
              AND effective_from <= ?
              AND (effective_to IS NULL OR effective_to >= ?)
              AND mandate_id <> ?
            """,
            Long.class,
            lenderId,
            custodianId,
            latestEnd,
            effectiveFrom,
            excludedMandateId == null ? NO_EXCLUDED_ID : excludedMandateId);
    return duplicates != null && duplicates > 0;
  }

  public boolean duplicateAgreement(
      Long mandateId,
      Long borrowerId,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      Long excludedAgreementId) {
    LocalDate latestEnd = effectiveTo == null ? LAST_SQL_DATE : effectiveTo;
    Long duplicates =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM msla_agreement
            WHERE mandate_id = ?
              AND borrower_org_id = ?
              AND effective_date <= ?
              AND (expiry_date IS NULL OR expiry_date >= ?)
              AND msla_id <> ?
            """,
            Long.class,
            mandateId,
            borrowerId,
            latestEnd,
            effectiveFrom,
            excludedAgreementId == null ? NO_EXCLUDED_ID : excludedAgreementId);
    return duplicates != null && duplicates > 0;
  }

  public boolean hasNonZeroCashHaircut(
      Long agreementId, LocalDate effectiveFrom, LocalDate effectiveTo) {
    LocalDateTime intervalStart = effectiveFrom.atStartOfDay();
    if (effectiveTo == null) {
      return existsNonZeroCashHaircutFrom(agreementId, intervalStart);
    }

    LocalDateTime intervalEnd = LocalDateTime.of(effectiveTo, LocalTime.MAX);
    return existsNonZeroCashHaircutDuring(agreementId, intervalStart, intervalEnd);
  }

  private boolean existsNonZeroCashHaircutFrom(
      Long agreementId, LocalDateTime intervalStart) {
    Boolean found =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1
              FROM collateral_haircut
              WHERE msla_id = ?
                AND collateral_type = 'CASH'
                AND haircut_percentage <> 0
                AND (effective_to IS NULL OR effective_to > ?)
            )
            """,
            Boolean.class,
            agreementId,
            intervalStart);
    return Boolean.TRUE.equals(found);
  }

  private boolean existsNonZeroCashHaircutDuring(
      Long agreementId, LocalDateTime intervalStart, LocalDateTime intervalEnd) {
    Boolean found =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
              SELECT 1
              FROM collateral_haircut
              WHERE msla_id = ?
                AND collateral_type = 'CASH'
                AND haircut_percentage <> 0
                AND effective_from <= ?
                AND (effective_to IS NULL OR effective_to > ?)
            )
            """,
            Boolean.class,
            agreementId,
            intervalEnd,
            intervalStart);
    return Boolean.TRUE.equals(found);
  }

  public boolean organizationShared(Long organizationId, Long custodianId) {
    boolean usedAsLenderByAnotherCustodian =
        !jdbc.queryForList(
                """
                SELECT mandate_id
                FROM lender_custodian_mandate
                WHERE lender_org_id = ?
                  AND custodian_org_id <> ?
                FOR UPDATE
                """,
                Long.class,
                organizationId,
                custodianId)
            .isEmpty();
    if (usedAsLenderByAnotherCustodian) {
      return true;
    }

    return !jdbc.queryForList(
            """
            SELECT agreement.msla_id
            FROM msla_agreement agreement
            JOIN lender_custodian_mandate mandate
              ON agreement.mandate_id = mandate.mandate_id
            WHERE agreement.borrower_org_id = ?
              AND mandate.custodian_org_id <> ?
            FOR UPDATE
            """,
            Long.class,
            organizationId,
            custodianId)
        .isEmpty();
  }
}
