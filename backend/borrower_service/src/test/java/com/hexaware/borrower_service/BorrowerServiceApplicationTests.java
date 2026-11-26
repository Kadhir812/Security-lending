package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.borrower_service.entity.InboxMessage;
import com.hexaware.borrower_service.enums.InboxMessageStatus;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(BorrowerServiceApplicationTests.TestConfiguration.class)
@TestPropertySource(
    properties = "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class BorrowerServiceApplicationTests {
  @Autowired private Environment environment;

  @Test
  void configurationUsesOnlyOwnedDatabaseAndUpdatesSchema() throws Exception {
    var properties = new Properties();
    try (var input = getClass().getResourceAsStream("/application.properties")) {
      properties.load(input);
    }
    assertThat(properties.getProperty("spring.datasource.url")).contains("/borrower_db?");
    assertThat(properties.getProperty("spring.datasource.username")).isEqualTo("${BORROWER_DB_USERNAME:root}");
    assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
    assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    assertThat(properties.getProperty("app.auth.issuer"))
        .isEqualTo("${JWT_ISSUER:securities-lending}");
    assertThat(properties.getProperty("app.auth.jwt-secret"))
        .startsWith("${JWT_SHARED_SECRET");
  }

  @Test
  void jwtSecretIsExplicitlyConfiguredForTests() {
    assertThat(environment.getRequiredProperty("app.auth.jwt-secret"))
        .isEqualTo("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
  }

  @Test
  void builderKeepsDefaults() {
    var first = InboxMessage.builder().consumerName("test").build();
    assertThat(first.getStatus()).isEqualTo(InboxMessageStatus.RECEIVED);
    assertThat(first.getReceivedAt()).isNotNull();
  }

  @Configuration
  static class TestConfiguration {}
}
