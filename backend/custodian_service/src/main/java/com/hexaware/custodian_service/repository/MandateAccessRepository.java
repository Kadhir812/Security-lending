package com.hexaware.custodian_service.repository;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only local authorization lookup, not a mandate business API. */
@Repository
@RequiredArgsConstructor
public class MandateAccessRepository {
  private final JdbcTemplate jdbc;

  public boolean isAccessible(String reference, String custodianOrganization, LocalDate today) {
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM lender_custodian_mandate m
            JOIN organization c ON c.organization_id = m.custodian_org_id
            JOIN organization l ON l.organization_id = m.lender_org_id
            WHERE BINARY m.mandate_reference = BINARY ? AND BINARY c.organization_reference = BINARY ?
              AND c.organization_type = 'CUSTODIAN' AND c.status = 'ACTIVE'
              AND l.organization_type = 'LENDER' AND l.status = 'ACTIVE'
              AND m.status = 'ACTIVE' AND m.effective_from <= ?
              AND (m.effective_to IS NULL OR m.effective_to >= ?)
            """,
            Integer.class,
            reference,
            custodianOrganization,
            today,
            today);
    return count != null && count == 1;
  }

  public boolean hasCurrentLenderMandate(
      String lenderReference, Long custodianId, LocalDate today, boolean lock) {
    if (lock) {
      return !jdbc.queryForList(
              """
              SELECT mandate.mandate_id
              FROM lender_custodian_mandate mandate
              JOIN organization lender ON lender.organization_id = mandate.lender_org_id
              WHERE BINARY lender.organization_reference = BINARY ?
                AND mandate.custodian_org_id = ?
                AND lender.organization_type = 'LENDER'
                AND lender.status = 'ACTIVE'
                AND mandate.status = 'ACTIVE'
                AND mandate.effective_from <= ?
                AND (mandate.effective_to IS NULL OR mandate.effective_to >= ?)
              FOR UPDATE
              """,
              Long.class,
              lenderReference,
              custodianId,
              today,
              today)
          .isEmpty();
    }
    Integer count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM lender_custodian_mandate mandate
            JOIN organization lender ON lender.organization_id = mandate.lender_org_id
            WHERE BINARY lender.organization_reference = BINARY ?
              AND mandate.custodian_org_id = ?
              AND lender.organization_type = 'LENDER'
              AND lender.status = 'ACTIVE'
              AND mandate.status = 'ACTIVE'
              AND mandate.effective_from <= ?
              AND (mandate.effective_to IS NULL OR mandate.effective_to >= ?)
            """,
            Integer.class,
            lenderReference,
            custodianId,
            today,
            today);
    return count != null && count > 0;
  }
}
