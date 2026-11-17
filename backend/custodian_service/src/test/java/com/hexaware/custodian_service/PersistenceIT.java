package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.*;

import com.hexaware.custodian_service.entity.InboxMessage;
import com.hexaware.custodian_service.entity.MessageLog;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.entity.OutboxEvent;
import com.hexaware.custodian_service.entity.Role;
import com.hexaware.custodian_service.entity.User;
import com.hexaware.custodian_service.entity.UserRole;
import com.hexaware.custodian_service.enums.InboxMessageStatus;
import com.hexaware.custodian_service.enums.MessageLogDirection;
import com.hexaware.custodian_service.enums.MessageLogProcessingStatus;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.OutboxEventStatus;
import com.hexaware.custodian_service.enums.UserStatus;
import com.hexaware.custodian_service.repository.InboxMessageRepository;
import com.hexaware.custodian_service.repository.MandateAccessRepository;
import com.hexaware.custodian_service.repository.MessageLogRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.repository.OutboxEventRepository;
import com.hexaware.custodian_service.repository.RoleRepository;
import com.hexaware.custodian_service.repository.UserRepository;
import com.hexaware.custodian_service.repository.UserRoleRepository;
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
@SpringBootTest(classes = CustodianServiceApplication.class)
@Transactional
class PersistenceIT {
  private static final String DATABASE = "custodian_db";
  private static final String USER = "stage1_custodian_db";
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
              new FileSystemResource(Path.of("../../db/02_custodian_db.sql").toAbsolutePath()));
        }
      }
      // Existing disposable Stage 1 databases need the additive Phase 3B audit columns.
      try (var columns = statement.executeQuery(
          "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='custodian_db'"
              + " AND table_name='organization' AND column_name='registered_by_custodian_id'")) {
        columns.next();
        if (columns.getInt(1) == 0) {
          ScriptUtils.executeSqlScript(connection, new FileSystemResource(
              Path.of("../../db/migrations/001_phase3b_custodian_setup.sql").toAbsolutePath()));
        }
      }
      try (var column =
          statement.executeQuery(
              "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='custodian_db'"
                  + " AND table_name='loan_request' AND column_name='settlement_currency'")) {
        column.next();
        if (column.getInt(1) == 0) {
          statement.execute(
              "ALTER TABLE custodian_db.loan_request ADD COLUMN settlement_currency CHAR(3) NULL");
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
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired UserRoleRepository userRoles;
  @Autowired OrganizationRepository organizations;
  @Autowired MandateAccessRepository mandates;

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

  @Test
  void userRoleMappingAndOptimisticVersionMatchSchema() {
    var user =
        User.builder()
            .identityIssuer("https://identity.example.test")
            .identitySubject("test-subject")
            .username("alice")
            .email("alice@example.test")
            .organization(
                organizations.saveAndFlush(
                    Organization.builder()
                        .organizationReference("ORG-A")
                        .organizationName("Custodian A")
                        .organizationType(OrganizationType.CUSTODIAN)
                        .build()))
            .build();
    users.saveAndFlush(user);
    var role = roles.saveAndFlush(Role.builder().roleName("TEST_ROLE").build());
    var assignment = userRoles.saveAndFlush(UserRole.builder().user(user).role(role).build());
    em.clear();
    var loaded =
        users
            .findByIdentityIssuerAndIdentitySubject("https://identity.example.test", "test-subject")
            .orElseThrow();
    assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(userRoles.findByUserUserId(loaded.getUserId()).get(0).getRole().getRoleName())
        .isEqualTo("TEST_ROLE");
    assertThat(assignment.getAssignedAt()).isNotNull();
    long oldVersion = loaded.getVersion();
    loaded.setPhone("555-0100");
    users.flush();
    assertThat(loaded.getVersion()).isEqualTo(oldVersion + 1);
    em.clear();
    user.setPhone("stale-update");
    assertThatThrownBy(() -> users.saveAndFlush(user))
        .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
  }

  @Test
  void duplicateUserRoleAndMissingForeignKeyAreRejected() {
    assertThatThrownBy(
            () -> jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (999999, 999999)"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var user =
        User.builder()
            .identityIssuer("https://identity.example.test")
            .identitySubject("test-subject")
            .username("alice")
            .email("alice@example.test")
            .organization(
                organizations.saveAndFlush(
                    Organization.builder()
                        .organizationReference("ORG-A")
                        .organizationName("Custodian A")
                        .organizationType(OrganizationType.CUSTODIAN)
                        .build()))
            .build();
    users.saveAndFlush(user);
    var role = roles.saveAndFlush(Role.builder().roleName("UNIQUE_ROLE").build());
    userRoles.saveAndFlush(UserRole.builder().user(user).role(role).build());
    assertThatThrownBy(
            () -> userRoles.saveAndFlush(UserRole.builder().user(user).role(role).build()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void mandateLookupChecksLocalOwnershipStatusAndDates() {
    var custodian =
        organizations.saveAndFlush(
            Organization.builder()
                .organizationReference("CUST-A")
                .organizationName("Custodian A")
                .organizationType(OrganizationType.CUSTODIAN)
                .build());
    var lender =
        organizations.saveAndFlush(
            Organization.builder()
                .organizationReference("LEND-A")
                .organizationName("Lender A")
                .organizationType(OrganizationType.LENDER)
                .build());
    jdbc.update(
        "INSERT INTO lender_custodian_mandate (lender_org_id, custodian_org_id, mandate_reference,"
            + " effective_from, effective_to) VALUES (?, ?, 'M-A', '2025-01-01', '2026-01-01')",
        lender.getOrganizationId(),
        custodian.getOrganizationId());
    assertThat(mandates.isAccessible("M-A", "CUST-A", java.time.LocalDate.of(2025, 6, 1))).isTrue();
    assertThat(mandates.isAccessible("M-A", "CUST-B", java.time.LocalDate.of(2025, 6, 1)))
        .isFalse();
    assertThat(mandates.isAccessible("M-A", "CUST-A", java.time.LocalDate.of(2026, 6, 1)))
        .isFalse();
    jdbc.update(
        "UPDATE lender_custodian_mandate SET status = 'INACTIVE' WHERE mandate_reference = 'M-A'");
    assertThat(mandates.isAccessible("M-A", "CUST-A", java.time.LocalDate.of(2025, 6, 1)))
        .isFalse();
  }
}
