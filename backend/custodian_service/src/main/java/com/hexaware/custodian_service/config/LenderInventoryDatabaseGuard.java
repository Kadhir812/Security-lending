package com.hexaware.custodian_service.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LenderInventoryDatabaseGuard {

  private final JdbcTemplate jdbcTemplate;

  public LenderInventoryDatabaseGuard(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @PostConstruct
  public void validate() {
    Integer matches =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.TABLE_CONSTRAINTS tc
            JOIN information_schema.KEY_COLUMN_USAGE kcu1
              ON tc.CONSTRAINT_SCHEMA = kcu1.TABLE_SCHEMA
             AND tc.TABLE_NAME = kcu1.TABLE_NAME
             AND tc.CONSTRAINT_NAME = kcu1.CONSTRAINT_NAME
            JOIN information_schema.KEY_COLUMN_USAGE kcu2
              ON tc.CONSTRAINT_SCHEMA = kcu2.TABLE_SCHEMA
             AND tc.TABLE_NAME = kcu2.TABLE_NAME
             AND tc.CONSTRAINT_NAME = kcu2.CONSTRAINT_NAME
            WHERE tc.TABLE_SCHEMA = DATABASE()
              AND tc.TABLE_NAME = 'lender_inventory'
              AND tc.CONSTRAINT_TYPE = 'UNIQUE'
              AND kcu1.COLUMN_NAME = 'organization_id'
              AND kcu2.COLUMN_NAME = 'security_isin'
            """,
            Integer.class);

    if (matches == null || matches == 0) {
      throw new IllegalStateException(
          "custodian_db.lender_inventory is missing the required UNIQUE(organization_id, security_isin) constraint. "
              + "This indicates the runtime database is stale or points at the wrong schema. "
              + "Repair the database schema instead of removing duplicate protection.");
    }
  }
}
