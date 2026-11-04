package com.hexaware.valuation_accural;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.valuation_accural.entity.InboxMessage;
import com.hexaware.valuation_accural.enums.InboxMessageStatus;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ValuationAccuralApplicationTests {
  @Test
  void configurationUsesOnlyOwnedDatabaseAndNeverCreatesSchema() throws Exception {
    var properties = new Properties();
    try (var input = getClass().getResourceAsStream("/application.properties")) {
      properties.load(input);
    }
    assertThat(properties.getProperty("spring.datasource.url")).contains("/valuation_accrual_db?");
    assertThat(properties.getProperty("spring.datasource.username"))
        .isEqualTo("${VALUATION_DB_USERNAME:root}");
    assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    assertThat(properties.getProperty("app.auth.issuer"))
        .isEqualTo("${JWT_ISSUER:securities-lending}");
    assertThat(properties.getProperty("app.auth.jwt-secret"))
        .isEqualTo("${JWT_SHARED_SECRET}");
  }

  @Test
  void builderKeepsDefaults() {
    var first = InboxMessage.builder().consumerName("test").build();
    assertThat(first.getStatus()).isEqualTo(InboxMessageStatus.RECEIVED);
    assertThat(first.getReceivedAt()).isNotNull();
  }
}
