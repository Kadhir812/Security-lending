package com.hexaware.security_master_market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Requires an explicitly supplied disposable MySQL server; never uses application credentials. */
@SpringBootTest(classes = SecurityMasterMarketApplication.class)
class PersistenceIT {
  private static final String DATABASE = "security_market_db";
  private static final String USER = "stage1_security_market_db";
  private static final String PASSWORD = UUID.randomUUID().toString();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws Exception {
    String server = System.getenv("STAGE1_MYSQL_URL");
    if (server == null || !server.endsWith("/")) {
      throw new IllegalStateException(
          "mysql-it requires STAGE1_MYSQL_URL=jdbc:mysql://host:port/ for a disposable server");
    }
    String admin = System.getenv().getOrDefault("STAGE1_MYSQL_ADMIN", "root");
    String adminPassword = System.getenv().getOrDefault("STAGE1_MYSQL_ADMIN_PASSWORD", "");
    try (var connection = DriverManager.getConnection(server, admin, adminPassword);
        var statement = connection.createStatement()) {
      try (var result =
          statement.executeQuery(
              "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '"
                  + DATABASE
                  + "'")) {
        result.next();
        if (result.getInt(1) == 0) {
          ScriptUtils.executeSqlScript(
              connection,
              new FileSystemResource(
                  Path.of("../../db/01_security_market_db.sql").toAbsolutePath()));
        }
      }
      statement.execute(
          "CREATE USER IF NOT EXISTS '" + USER + "'@'localhost' IDENTIFIED BY '" + PASSWORD + "'");
      statement.execute("ALTER USER '" + USER + "'@'localhost' IDENTIFIED BY '" + PASSWORD + "'");
      statement.execute(
          "GRANT SELECT, INSERT, UPDATE ON " + DATABASE + ".* TO '" + USER + "'@'localhost'");
    }
    registry.add(
        "spring.datasource.url",
        () -> server + DATABASE + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
    registry.add("spring.datasource.username", () -> USER);
    registry.add("spring.datasource.password", () -> PASSWORD);
    registry.add("app.auth.preview-subject", () -> "preview-subject");
    registry.add("app.auth.custodian-subject", () -> "custodian-subject");
  }

  @Autowired JdbcTemplate jdbc;

  @Test
  void credentialsCannotReadOtherDatabasesOrCreateTables() {
    assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(DATABASE);
    for (String other :
        new String[] {
          "borrower_db", "custodian_db", "valuation_accrual_db", "security_market_db"
        }) {
      if (!other.equals(DATABASE)) {
        assertThatThrownBy(
                () ->
                    jdbc.queryForObject(
                        "SELECT COUNT(*) FROM " + other + ".message_log", Long.class))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
      }
    }
    assertThatThrownBy(() -> jdbc.execute("CREATE TABLE forbidden_schema_change (id BIGINT)"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
