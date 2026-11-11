package com.hexaware.valuation_accural;

import static org.assertj.core.api.Assertions.*;

import com.hexaware.valuation_accural.entity.InboxMessage;
import com.hexaware.valuation_accural.entity.MessageLog;
import com.hexaware.valuation_accural.entity.OutboxEvent;
import com.hexaware.valuation_accural.enums.InboxMessageStatus;
import com.hexaware.valuation_accural.enums.MessageLogDirection;
import com.hexaware.valuation_accural.enums.MessageLogProcessingStatus;
import com.hexaware.valuation_accural.enums.OutboxEventStatus;
import com.hexaware.valuation_accural.repository.InboxMessageRepository;
import com.hexaware.valuation_accural.repository.MessageLogRepository;
import com.hexaware.valuation_accural.repository.OutboxEventRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Requires an explicitly supplied disposable MySQL server; never uses application credentials. */
@SpringBootTest(classes = ValuationAccuralApplication.class)
@Transactional
class PersistenceIT {
  private static final String DATABASE = "valuation_accrual_db";
  private static final String USER = "stage1_valuation_accrual_db";
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
                  Path.of("../../db/04_valuation_accrual_db.sql").toAbsolutePath()));
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
    registry.add("app.auth.issuer", () -> "securities-lending");
    registry.add("app.auth.preview-subject", () -> "preview-subject");
    registry.add("app.auth.custodian-subject", () -> "custodian-subject");
  }

  @Autowired MessageLogRepository messages;
  @Autowired OutboxEventRepository outbox;
  @Autowired InboxMessageRepository inbox;
  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;

  private InboxMessage inbox(String event) {
    return InboxMessage.builder()
        .consumerName("test-consumer")
        .eventId(event)
        .correlationId("correlation")
        .aggregateReference("aggregate")
        .aggregateVersion(1L)
        .eventType("FOUNDATION_TEST")
        .payload(Map.of("schemaVersion", 1, "text", "round-trip"))
        .kafkaTopic("test")
        .kafkaPartition(0)
        .kafkaOffset(0L)
        .build();
  }

  @Test
  void messagingJsonEnumsUtcTimestampsAndDefaultsRoundTrip() {
    String event = UUID.randomUUID().toString();
    Instant time = Instant.parse("2026-01-02T03:04:05.123456Z");
    var message =
        messages.saveAndFlush(
            MessageLog.builder()
                .producerService("TEST")
                .eventId(event)
                .serviceName("TEST")
                .direction(MessageLogDirection.OUTBOUND)
                .correlationId("correlation")
                .applicationMessageType("FOUNDATION_TEST")
                .payload(Map.of("schemaVersion", 1, "nested", Map.of("value", "exact")))
                .createdAt(time)
                .rawFix("raw audit payload")
                .build());
    var outgoing =
        outbox.saveAndFlush(
            OutboxEvent.builder()
                .producerService("TEST")
                .eventId(event)
                .serviceName("TEST")
                .aggregateType("TEST")
                .aggregateReference("aggregate")
                .aggregateVersion(0L)
                .correlationId("correlation")
                .eventType("FOUNDATION_TEST")
                .kafkaTopic("test")
                .partitionKey("aggregate")
                .payload(Map.of("schemaVersion", 1))
                .build());
    var incoming = inbox.saveAndFlush(inbox(event));
    em.clear();
    var stored = messages.findById(message.getMessageLogId()).orElseThrow();
    assertThat(stored.getPayload()).containsEntry("nested", Map.of("value", "exact"));
    assertThat(stored.getCreatedAt()).isEqualTo(time);
    assertThat(stored.getProcessingStatus()).isEqualTo(MessageLogProcessingStatus.PENDING);
    assertThat(outbox.findById(outgoing.getOutboxId()).orElseThrow().getStatus())
        .isEqualTo(OutboxEventStatus.PENDING);
    assertThat(outgoing.getAttemptCount()).isZero();
    assertThat(inbox.findById(incoming.getInboxId()).orElseThrow().getStatus())
        .isEqualTo(InboxMessageStatus.RECEIVED);
  }

  @Test
  void duplicateConsumerEventIsRejected() {
    String event = UUID.randomUUID().toString();
    inbox.saveAndFlush(inbox(event));
    assertThatThrownBy(() -> inbox.saveAndFlush(inbox(event)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void databaseChecksRejectNegativeOffsets() {
    var message = inbox(UUID.randomUUID().toString());
    message.setKafkaOffset(-1L);
    assertThatThrownBy(() -> inbox.saveAndFlush(message))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

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
