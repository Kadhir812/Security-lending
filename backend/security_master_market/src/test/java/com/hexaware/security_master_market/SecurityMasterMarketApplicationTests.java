package com.hexaware.security_master_market;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class SecurityMasterMarketApplicationTests {
  @Test
  void configurationUsesOnlyOwnedDatabaseAndNeverCreatesSchema() throws Exception {
    var properties = new Properties();
    try (var input = getClass().getResourceAsStream("/application.properties")) {
      properties.load(input);
    }
    assertThat(properties.getProperty("spring.datasource.url")).contains("/security_market_db?");
    assertThat(properties.getProperty("spring.datasource.username"))
        .isEqualTo("${MARKET_DB_USERNAME:root}");
    assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    assertThat(properties.getProperty("app.auth.issuer")).isEqualTo("${JWT_ISSUER:securities-lending}");
    assertThat(properties.getProperty("app.auth.jwt-secret")).isEqualTo("${JWT_SHARED_SECRET}");
  }
}
