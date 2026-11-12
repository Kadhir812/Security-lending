package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.custodian_service.entity.InboxMessage;
import com.hexaware.custodian_service.enums.InboxMessageStatus;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class CustodianServiceApplicationTests {
  @Test
  void configurationUsesOnlyOwnedDatabaseAndUpdatesSchema() throws Exception {
    var properties = new Properties();
    try (var input = getClass().getResourceAsStream("/application.properties")) {
      properties.load(input);
    }
    assertThat(properties.getProperty("spring.datasource.url")).contains("/custodian_db?");
    assertThat(properties.getProperty("spring.datasource.username")).isEqualTo("${CUSTODIAN_DB_USERNAME:root}");
    assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
    assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    assertThat(properties.getProperty("app.auth.issuer"))
        .isEqualTo("${JWT_ISSUER:securities-lending}");
    assertThat(properties.getProperty("app.auth.jwt-secret"))
        .startsWith("${JWT_SHARED_SECRET");
  }

  @Test
  void builderKeepsDefaults() {
    var first = InboxMessage.builder().consumerName("test").build();
    assertThat(first.getStatus()).isEqualTo(InboxMessageStatus.RECEIVED);
    assertThat(first.getReceivedAt()).isNotNull();
  }
}
