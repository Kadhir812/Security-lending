package com.hexaware.borrower_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Runs against four already-started services and a disposable, seeded loan fixture. */
class LoanWorkflowE2EIT {
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final long FLOW_TIMEOUT_MILLIS = 60_000;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void submitsApprovesAndRejectsLoansAcrossKafkaAndServiceBoundaries() throws Exception {
    String borrowerUrl = required("LOAN_E2E_BORROWER_URL");
    String custodianUrl = required("LOAN_E2E_CUSTODIAN_URL");
    String borrowerToken = required("LOAN_E2E_BORROWER_TOKEN");
    String custodianToken = required("LOAN_E2E_CUSTODIAN_TOKEN");
    String mslaReference = required("LOAN_E2E_MSLA_REFERENCE");
    String isin = System.getenv().getOrDefault("LOAN_E2E_SECURITY_ISIN", "INE000A01012");
    String currency = System.getenv().getOrDefault("LOAN_E2E_CURRENCY", "INR");
    long quantity = Long.parseLong(System.getenv().getOrDefault("LOAN_E2E_QUANTITY", "100"));
    LocalDate settlementDate =
        LocalDate.parse(
            System.getenv()
                .getOrDefault(
                    "LOAN_E2E_SETTLEMENT_DATE",
                    LocalDate.now(ZoneOffset.UTC).plusDays(3).toString()));

    try (var connection =
        DriverManager.getConnection(
            required("LOAN_E2E_CUSTODIAN_JDBC_URL"),
            required("LOAN_E2E_CUSTODIAN_DB_USER"),
            required("LOAN_E2E_CUSTODIAN_DB_PASSWORD"))) {
      String database = connection.getCatalog();
      assertThat(database)
          .as("Custodian integration assertions must use a disposable schema")
          .startsWith("sl3d3e_test_");

      JsonNode eligible = get(borrowerUrl, "/api/loan-requests/eligible-mslas", borrowerToken);
      JsonNode msla =
          findMsla(eligible, mslaReference)
              .orElseThrow(() -> new AssertionError("Configured MSLA is not borrower-eligible"));
      assertThat(msla.path("baseCurrency").asText()).isEqualTo(currency);
      BigDecimal reservedCreditBefore = reservedCredit(connection, mslaReference);

      String approvalKey = "loan-e2e-approve-" + UUID.randomUUID();
      JsonNode approvalRequest =
          submission(mslaReference, isin, currency, quantity, settlementDate);
      JsonNode submitted =
          post(borrowerUrl, "/api/loan-requests", borrowerToken, approvalKey, approvalRequest, 201);
      String approvalReference = submitted.path("requestReference").asText();
      assertThat(approvalReference).isNotBlank();
      JsonNode replay =
          post(borrowerUrl, "/api/loan-requests", borrowerToken, approvalKey, approvalRequest, 201);
      assertThat(replay.path("requestReference").asText()).isEqualTo(approvalReference);
      HttpResponse<String> conflictingReplay =
          send(
              borrowerUrl,
              "/api/loan-requests",
              borrowerToken,
              "POST",
              approvalKey,
              submission(mslaReference, isin, currency, quantity + 1, settlementDate));
      assertThat(conflictingReplay.statusCode()).isEqualTo(409);

      JsonNode received =
          awaitJson(
              "Custodian receipt",
              () -> getIfSuccessful(custodianUrl, "/api/loan-requests/" + approvalReference, custodianToken));
      assertThat(received.path("status").asText()).isEqualTo("PENDING");
      assertThat(outboxCount(connection, approvalReference, "LOAN_REQUEST_SUBMITTED")).isEqualTo(1);
      assertThat(inboxCount(connection, approvalReference, "LOAN_REQUEST_SUBMITTED")).isEqualTo(1);

      JsonNode approved =
          post(
              custodianUrl,
              "/api/loan-requests/" + approvalReference + "/approve",
              custodianToken,
              null,
              null,
              200);
      assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
      String contractReference = approved.path("contractReference").asText();
      assertThat(contractReference).isNotBlank();
      awaitJson(
          "Borrower approval decision",
          () -> {
            JsonNode request = get(borrowerUrl, "/api/loan-requests/" + approvalReference, borrowerToken);
            return "APPROVED".equals(request.path("status").asText()) ? request : null;
          });
      JsonNode contractView =
          awaitJson(
              "Borrower contract snapshot",
              () ->
                  getIfSuccessful(
                      borrowerUrl,
                      "/api/loan-requests/" + approvalReference + "/contract",
                      borrowerToken));

      JsonNode repeatedApproval =
          post(
              custodianUrl,
              "/api/loan-requests/" + approvalReference + "/approve",
              custodianToken,
              null,
              null,
              200);
      assertThat(repeatedApproval.path("contractReference").asText()).isEqualTo(contractReference);
      assertThat(contractCount(connection, approvalReference)).isEqualTo(1);
      assertThat(activeReservationQuantity(connection, approvalReference)).isEqualTo(quantity);
      assertThat(activeReservationCount(connection, approvalReference)).isEqualTo(1);
      assertThat(
              json.readTree(contractCollateralTerms(connection, approvalReference)))
          .isEqualTo(contractView.path("contractSnapshot").path("collateralTerms"));
      BigDecimal reservedCreditAfter = reservedCredit(connection, mslaReference);
      BigDecimal contractCredit = contractReservedCredit(connection, approvalReference);
      assertThat(reservedCreditAfter.subtract(reservedCreditBefore)).isEqualByComparingTo(contractCredit);
      assertThat(contractCredit)
          .isEqualByComparingTo(new BigDecimal(approved.path("loanMarketValue").asText()));
      assertThat(inboxCount(connection, approvalReference, "LOAN_REQUEST_DECISION")).isEqualTo(1);
      assertThat(outboxCount(connection, approvalReference, "LOAN_REQUEST_DECISION")).isEqualTo(1);

      String rejectionKey = "loan-e2e-reject-" + UUID.randomUUID();
      JsonNode rejectionRequest =
          post(
              borrowerUrl,
              "/api/loan-requests",
              borrowerToken,
              rejectionKey,
              submission(mslaReference, isin, currency, quantity, settlementDate),
              201);
      String rejectionReference = rejectionRequest.path("requestReference").asText();
      awaitJson(
          "Custodian receipt for rejection",
          () ->
              getIfSuccessful(
                  custodianUrl, "/api/loan-requests/" + rejectionReference, custodianToken));
      BigDecimal reservedCreditBeforeRejection = reservedCredit(connection, mslaReference);
      String reason = "Automated 3D/3E E2E rejection";
      JsonNode rejected =
          post(
              custodianUrl,
              "/api/loan-requests/" + rejectionReference + "/reject",
              custodianToken,
              null,
              json.createObjectNode().put("reason", reason),
              200);
      assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
      assertThat(rejected.path("reason").asText()).isEqualTo(reason);
      JsonNode repeatedRejection =
          post(
              custodianUrl,
              "/api/loan-requests/" + rejectionReference + "/reject",
              custodianToken,
              null,
              json.createObjectNode().put("reason", reason),
              200);
      assertThat(repeatedRejection.path("status").asText()).isEqualTo("REJECTED");
      awaitJson(
          "Borrower rejection decision",
          () -> {
            JsonNode request =
                get(borrowerUrl, "/api/loan-requests/" + rejectionReference, borrowerToken);
            return "REJECTED".equals(request.path("status").asText()) ? request : null;
          });
      assertThat(contractCount(connection, rejectionReference)).isZero();
      assertThat(activeReservationCount(connection, rejectionReference)).isZero();
      assertThat(reservedCredit(connection, mslaReference))
          .isEqualByComparingTo(reservedCreditBeforeRejection);
      assertThat(inboxCount(connection, rejectionReference, "LOAN_REQUEST_SUBMITTED")).isEqualTo(1);
      assertThat(inboxCount(connection, rejectionReference, "LOAN_REQUEST_DECISION")).isEqualTo(1);
      assertThat(outboxCount(connection, rejectionReference, "LOAN_REQUEST_DECISION")).isEqualTo(1);
      HttpResponse<String> rejectedContract =
          send(
              borrowerUrl,
              "/api/loan-requests/" + rejectionReference + "/contract",
              borrowerToken,
              "GET",
              null,
              null);
      assertThat(rejectedContract.statusCode()).isEqualTo(404);
    }
  }

  private JsonNode submission(
      String msla, String isin, String currency, long quantity, LocalDate settlementDate) {
    return json
        .createObjectNode()
        .put("mslaReference", msla)
        .put("securityIsin", isin)
        .put("quantity", quantity)
        .put("requestedSettlementDate", settlementDate.toString())
        .put("settlementCurrency", currency)
        .putNull("locateReference");
  }

  private java.util.Optional<JsonNode> findMsla(JsonNode agreements, String reference) {
    for (JsonNode agreement : agreements) {
      if (reference.equals(agreement.path("mslaReference").asText())) {
        return java.util.Optional.of(agreement);
      }
    }
    return java.util.Optional.empty();
  }

  private JsonNode get(String base, String path, String token) throws Exception {
    HttpResponse<String> response = send(base, path, token, "GET", null, null);
    assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
    return json.readTree(response.body());
  }

  private JsonNode getIfSuccessful(String base, String path, String token) throws Exception {
    HttpResponse<String> response = send(base, path, token, "GET", null, null);
    return response.statusCode() == 200 ? json.readTree(response.body()) : null;
  }

  private JsonNode post(
      String base, String path, String token, String idempotencyKey, JsonNode body, int expected)
      throws Exception {
    HttpResponse<String> response = send(base, path, token, "POST", idempotencyKey, body);
    assertThat(response.statusCode()).as("POST %s", path).isEqualTo(expected);
    return json.readTree(response.body());
  }

  private HttpResponse<String> send(
      String base, String path, String token, String method, String idempotencyKey, JsonNode body)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/json");
    if ("POST".equals(method)) {
      request.header("Content-Type", "application/json");
      if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
      request.POST(
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    } else {
      request.GET();
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode awaitJson(String description, JsonSupplier action) throws Exception {
    long deadline = System.nanoTime() + Duration.ofMillis(FLOW_TIMEOUT_MILLIS).toNanos();
    while (System.nanoTime() < deadline) {
      JsonNode result = action.get();
      if (result != null) return result;
      Thread.sleep(250);
    }
    throw new AssertionError("Timed out waiting for " + description);
  }

  private long contractCount(java.sql.Connection connection, String reference) throws Exception {
    return queryLong(
        connection,
        "SELECT COUNT(*) FROM loan_contract lc JOIN loan_request lr"
            + " ON lr.request_id=lc.loan_request_id WHERE lr.request_reference=?",
        reference);
  }

  private long activeReservationCount(java.sql.Connection connection, String reference)
      throws Exception {
    return queryLong(
        connection,
        "SELECT COUNT(*) FROM inventory_reservation ir JOIN loan_contract lc"
            + " ON lc.contract_id=ir.contract_id JOIN loan_request lr"
            + " ON lr.request_id=lc.loan_request_id"
            + " WHERE lr.request_reference=? AND ir.status='ACTIVE'",
        reference);
  }

  private long activeReservationQuantity(java.sql.Connection connection, String reference)
      throws Exception {
    return queryLong(
        connection,
        "SELECT COALESCE(SUM(ir.reserved_quantity),0) FROM inventory_reservation ir"
            + " JOIN loan_contract lc ON lc.contract_id=ir.contract_id"
            + " JOIN loan_request lr ON lr.request_id=lc.loan_request_id"
            + " WHERE lr.request_reference=? AND ir.status='ACTIVE'",
        reference);
  }

  private BigDecimal reservedCredit(java.sql.Connection connection, String mslaReference)
      throws Exception {
    try (var statement =
        connection.prepareStatement(
            "SELECT c.reserved_amount FROM counterparty_credit_limit c JOIN msla_agreement m"
                + " ON m.msla_id=c.msla_id WHERE m.msla_reference=?")) {
      statement.setString(1, mslaReference);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).as("Credit limit fixture exists").isTrue();
        BigDecimal amount = result.getBigDecimal(1);
        assertThat(result.next()).as("MSLA has one credit limit").isFalse();
        return amount;
      }
    }
  }

  private BigDecimal contractReservedCredit(java.sql.Connection connection, String reference)
      throws Exception {
    try (var statement =
        connection.prepareStatement(
            "SELECT lc.reserved_credit_amount FROM loan_contract lc JOIN loan_request lr"
                + " ON lr.request_id=lc.loan_request_id WHERE lr.request_reference=?")) {
      statement.setString(1, reference);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getBigDecimal(1);
      }
    }
  }

  private String contractCollateralTerms(java.sql.Connection connection, String reference)
      throws Exception {
    try (var statement =
        connection.prepareStatement(
            "SELECT lc.collateral_terms FROM loan_contract lc JOIN loan_request lr"
                + " ON lr.request_id=lc.loan_request_id WHERE lr.request_reference=?")) {
      statement.setString(1, reference);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getString(1);
      }
    }
  }

  private long inboxCount(java.sql.Connection connection, String reference, String eventType)
      throws Exception {
    return queryLong(
        connection,
        "SELECT COUNT(*) FROM inbox_message WHERE aggregate_reference=? AND event_type=?"
            + " AND status='PROCESSED'",
        reference,
        eventType);
  }

  private long outboxCount(java.sql.Connection connection, String reference, String eventType)
      throws Exception {
    return queryLong(
        connection,
        "SELECT COUNT(*) FROM outbox_event WHERE aggregate_reference=? AND event_type=?"
            + " AND status='PUBLISHED'",
        reference,
        eventType);
  }

  private long queryLong(java.sql.Connection connection, String sql, String... parameters)
      throws Exception {
    try (var statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) statement.setString(i + 1, parameters[i]);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getLong(1);
      }
    }
  }

  private String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Set " + name + " for the isolated loan E2E fixture");
    }
    return value;
  }

  @FunctionalInterface
  private interface JsonSupplier {
    JsonNode get() throws Exception;
  }
}
