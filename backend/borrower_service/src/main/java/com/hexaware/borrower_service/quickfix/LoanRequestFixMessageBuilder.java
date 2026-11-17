package com.hexaware.borrower_service.quickfix;

import com.hexaware.borrower_service.kafka.event.EventEnvelope;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.FieldException;
import quickfix.FieldMap;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.ValidationSettings;

@Component
public class LoanRequestFixMessageBuilder {
  private static final String BEGIN_STRING = "FIX.4.4";
  private static final String MSG_TYPE = "U1";
  private static final char SOH = '\u0001';
  private static final DateTimeFormatter SENDING_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter SETTLEMENT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final DataDictionary dictionary;

  public LoanRequestFixMessageBuilder() {
    try (var input = new ClassPathResource("quickfix/FIX44_SL.xml").getInputStream()) {
      dictionary = new DataDictionary(input);
    } catch (IOException | ConfigError exception) {
      throw new IllegalStateException("Unable to load FIX 4.4 loan-request dictionary", exception);
    }
  }

  public String build(EventEnvelope event, Map<String, ?> businessFields) {
    Message message = new Message();
    setHeader(message, event, MSG_TYPE);

    message.setString(9001, text(businessFields.get("requestReference"), "requestReference"));
    message.setString(9002, text(businessFields.get("mslaReference"), "mslaReference"));
    message.setString(48, text(businessFields.get("securityIsin"), "securityIsin"));
    message.setString(22, "4");
    message.setString(38, quantity(businessFields.get("quantity")));
    message.setString(15, text(businessFields.get("settlementCurrency"), "settlementCurrency"));
    message.setString(64, date(businessFields.get("requestedSettlementDate")));
    message.setString(9003, text(businessFields.get("submittedByIssuer"), "submittedByIssuer"));
    message.setString(9004, text(businessFields.get("submittedBySubject"), "submittedBySubject"));
    Object locateReference = businessFields.get("locateReference");
    if (locateReference != null) {
      message.setString(9005, text(locateReference, "locateReference"));
    }

    String wireMessage = message.toString();
    validateForEnvelope(event, wireMessage);
    return wireMessage;
  }

  public String buildCollateralInquiry(
      EventEnvelope event,
      String inquiryId,
      String requestReference,
      String mslaReference) {
    Message message = new Message();
    setHeader(message, event, "BB");
    message.setString(909, uuid(inquiryId));
    message.setString(9001, text(requestReference, "requestReference"));
    message.setString(9002, text(mslaReference, "mslaReference"));

    String wireMessage = message.toString();
    validateCollateralInquiryForEnvelope(event, wireMessage);
    return wireMessage;
  }

  public String fromLegacyEnvelope(EventEnvelope event) {
    Map<String, Object> legacy = event.getPayload();
    if (number(legacy.get("payloadVersion")) != 1) {
      throw new IllegalArgumentException("Unsupported legacy loan-request payload");
    }
    return build(event, legacy);
  }

  public Message parseAndValidate(String wireMessage) {
    Message message = parseMessage(wireMessage);
    if (!MSG_TYPE.equals(string(message.getHeader(), 35))) {
      throw new IllegalArgumentException("Unexpected FIX begin string or message type");
    }
    return message;
  }

  public Message parseCollateralInquiryAndValidate(String wireMessage) {
    Message message = parseMessage(wireMessage);
    if (!"BB".equals(string(message.getHeader(), 35))) {
      throw new IllegalArgumentException("Unexpected FIX begin string or message type");
    }
    return message;
  }

  public Message parseCollateralReport(String wireMessage) {
    Message message = parseMessage(wireMessage);
    if (!"BA".equals(string(message.getHeader(), 35))) {
      throw new IllegalArgumentException("Unexpected FIX collateral report message type");
    }
    requireUuid(string(message, 908), "report ID");
    requireUuid(string(message, 909), "inquiry ID");
    try {
      BigDecimal haircut = new BigDecimal(string(message, 9011));
      BigDecimal coverage = new BigDecimal(string(message, 9012));
      if (integer(message, 910) != 0
          || !"CASH".equals(string(message, 9010))
          || haircut.compareTo(BigDecimal.ZERO) != 0
          || coverage.compareTo(BigDecimal.valueOf(100)) < 0
          || coverage.compareTo(BigDecimal.valueOf(100_000)) >= 0
          || !string(message, 918).matches("[A-Z]{3}")) {
        throw new IllegalArgumentException("Invalid FIX collateral report terms");
      }
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid FIX collateral report percentage", exception);
    }
    return message;
  }

  public Map<String, Object> parseApprovalConfirmationAndValidate(
      EventEnvelope event, String wireMessage) {
    Message message = parseMessage(wireMessage);
    var header = message.getHeader();
    String requestReference = string(message, 9001);
    long decisionVersion = longValue(message, 9100);
    if (!"AK".equals(string(header, 35))
        || !required(event.getSenderOrganizationReference(), "sender")
            .equals(string(header, 49))
        || !required(event.getReceiverOrganizationReference(), "receiver")
            .equals(string(header, 56))
        || Integer.parseInt(string(header, 34))
            != Math.toIntExact(event.getAggregateVersion() + 1)
        || !SENDING_TIME.format(event.getOccurredAt()).equals(string(header, 52))
        || !event.getEventId().equals(string(message, 9134))
        || !SENDING_TIME.format(timestamp(string(message, 9135)))
            .equals(string(message, 60))
        || !event.getAggregateReference().equals(requestReference)
        || !event.getCorrelationId().equals(requestReference)
        || decisionVersion != event.getAggregateVersion()
        || !"APPROVED".equals(string(message, 9101))
        || integer(message, 666) != 0
        || integer(message, 773) != 2
        || integer(message, 665) != 4) {
      throw new IllegalArgumentException("FIX AK fields do not match decision envelope");
    }

    Map<String, Object> collateralTerms = new java.util.LinkedHashMap<>();
    collateralTerms.put("collateralType", string(message, 9108));
    collateralTerms.put("currency", string(message, 9109));
    collateralTerms.put("haircutId", longValue(message, 9110));
    collateralTerms.put("haircutPercentage", decimal(message, 9111));
    collateralTerms.put("coveragePercentage", decimal(message, 9112));
    collateralTerms.put("requiredAmount", decimal(message, 9113));
    collateralTerms.put("loanMarketValue", decimal(message, 9114));
    collateralTerms.put("valuationReference", string(message, 9115));

    Map<String, Object> valuation = new java.util.LinkedHashMap<>();
    valuation.put("calculationReference", string(message, 9116));
    valuation.put("securityIsin", string(message, 9117));
    valuation.put("quantity", longValue(message, 9118));
    valuation.put("marketPrice", decimal(message, 9119));
    valuation.put("currency", string(message, 9120));
    valuation.put("priceReference", string(message, 9121));
    valuation.put("priceSource", string(message, 9122));
    valuation.put("priceTimestamp", string(message, 9123));
    valuation.put("calculatedAt", string(message, 9124));
    valuation.put("loanMarketValue", decimal(message, 9125));
    valuation.put("requiredCashCollateral", decimal(message, 9126));
    valuation.put("coveragePercentage", decimal(message, 9127));
    valuation.put("cashHaircutPercentage", decimal(message, 9128));
    valuation.put("creditReservationAmount", decimal(message, 9129));

    Map<String, Object> contract = new java.util.LinkedHashMap<>();
    contract.put("requestReference", requestReference);
    contract.put("securityIsin", string(message, 9102));
    contract.put("transactionReference", string(message, 664));
    contract.put("quantity", longValue(message, 9103));
    contract.put("settlementCurrency", string(message, 9104));
    contract.put("agreedAnnualFeePct", decimal(message, 9105));
    contract.put("dayCountBasis", string(message, 9106));
    contract.put("collateralCoveragePct", decimal(message, 9107));
    contract.put("collateralTerms", collateralTerms);
    contract.put("acceptedValuationSnapshot", valuation);
    contract.put("loanMarketValue", decimal(message, 9130));
    contract.put("requiredCashCollateral", decimal(message, 9131));
    contract.put("settlementCalendarVersion", string(message, 9132));
    contract.put("tradeDate", LocalDate.parse(string(message, 75), SETTLEMENT_DATE).toString());
    contract.put(
        "settlementDate", LocalDate.parse(string(message, 9133), SETTLEMENT_DATE).toString());

    Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("requestReference", requestReference);
    payload.put("mslaReference", string(message, 9002));
    payload.put("borrowerOrganizationReference", event.getReceiverOrganizationReference());
    payload.put("custodianOrganizationReference", event.getSenderOrganizationReference());
    payload.put("decisionStatus", "APPROVED");
    payload.put("decisionReason", null);
    payload.put("decisionVersion", decisionVersion);
    payload.put("respondedAt", timestamp(string(message, 9135)));
    payload.put("contract", contract);
    return payload;
  }

  public Map<String, Object> parseSettlementStatusAndValidate(
      EventEnvelope event, String wireMessage) {
    Message message = parseMessage(wireMessage);
    var header = message.getHeader();
    String instructionReference = string(message, 9141);
    String settledAt = string(message, 9153);
    long aggregateVersion = longValue(message, 9152);
    long quantity = longValue(message, 9154);
    long deliveredQuantity = longValue(message, 9146);
    long outstandingQuantity = longValue(message, 9147);
    long contractVersion = longValue(message, 9145);
    BigDecimal confirmedCashCollateral = decimal(message, 9148);
    Instant settlementTime = timestamp(settledAt);
    if (!"SETTLEMENT_INSTRUCTION_UPDATED".equals(event.getEventType())
        || !"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || event.getOccurredAt() == null
        || event.getAggregateVersion() == null
        || !"U2".equals(string(header, 35))
        || !required(event.getSenderOrganizationReference(), "sender")
            .equals(string(header, 49))
        || !required(event.getReceiverOrganizationReference(), "receiver")
            .equals(string(header, 56))
        || Integer.parseInt(string(header, 34))
            != Math.toIntExact(event.getAggregateVersion() + 1)
        || !SENDING_TIME.format(event.getOccurredAt()).equals(string(header, 52))
        || !required(event.getEventId(), "event ID").equals(string(message, 9151))
        || !event.getAggregateReference().equals(instructionReference)
        || !event.getCorrelationId().equals(instructionReference)
        || event.getAggregateVersion() != 1L
        || aggregateVersion != event.getAggregateVersion()
        || contractVersion <= 0
        || !"SECURITY_DELIVERY".equals(string(message, 9142))
        || !"SETTLED".equals(string(message, 9143))
        || !"ON_LOAN".equals(string(message, 9144))
        || !"Y".equals(string(message, 9150))
        || quantity <= 0
        || deliveredQuantity != quantity
        || outstandingQuantity != quantity
        || confirmedCashCollateral.signum() < 0
        || !string(message, 15).matches("[A-Z]{3}")
        || !string(message, 48).matches("[A-Z0-9]{12}")
        || !"4".equals(string(message, 22))
        || settlementTime.isAfter(event.getOccurredAt())
        || !SENDING_TIME.format(settlementTime).equals(string(message, 60))) {
      throw new IllegalArgumentException("FIX U2 fields do not match settlement envelope");
    }

    Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("payloadVersion", 1);
    payload.put("aggregateVersion", aggregateVersion);
    payload.put("transactionReference", string(message, 9140));
    payload.put("instructionReference", instructionReference);
    payload.put("instructionType", "SECURITY_DELIVERY");
    payload.put("borrowerOrganizationReference", string(header, 56));
    payload.put("custodianOrganizationReference", string(header, 49));
    payload.put("settlementStatus", "SETTLED");
    payload.put("instructionStatus", "SETTLED");
    payload.put("currency", string(message, 15));
    payload.put("quantity", quantity);
    payload.put("securityIsin", string(message, 48));
    payload.put("contractStatus", "ON_LOAN");
    payload.put("contractVersion", contractVersion);
    payload.put("deliveredQuantity", deliveredQuantity);
    payload.put("outstandingQuantity", outstandingQuantity);
    payload.put("confirmedCashCollateral", confirmedCashCollateral.toPlainString());
    payload.put("settledAt", settledAt);
    payload.put("externalConfirmationReference", string(message, 9149));
    payload.put("simulatedConfirmation", true);
    return payload;
  }

  public void validateCollateralReportForEnvelope(EventEnvelope event, String wireMessage) {
    Message message = parseCollateralReport(wireMessage);
    var header = message.getHeader();
    if (!required(event.getSenderOrganizationReference(), "sender")
            .equals(string(header, 49))
        || !required(event.getReceiverOrganizationReference(), "receiver")
            .equals(string(header, 56))
        || Integer.parseInt(string(header, 34))
            != Math.toIntExact(event.getAggregateVersion() + 1)
        || !SENDING_TIME.format(event.getOccurredAt()).equals(string(header, 52))
        || !event.getCorrelationId().equals(string(message, 909))
        || !event.getAggregateReference().equals(string(message, 9001))) {
      throw new IllegalArgumentException("FIX BA fields do not match Kafka envelope metadata");
    }
  }

  private Message parseMessage(String wireMessage) {
    validateFrame(wireMessage);
    Message message = new Message();
    try {
      ValidationSettings validationSettings = new ValidationSettings();
      message.fromString(wireMessage, dictionary, validationSettings, true);
      dictionary.validate(message, validationSettings);
    } catch (InvalidMessage
        | FieldException
        | IncorrectDataFormat
        | IncorrectTagValue
        | quickfix.FieldNotFound exception) {
      throw new IllegalArgumentException("Invalid FIX 4.4 message", exception);
    }
    if (!BEGIN_STRING.equals(string(message.getHeader(), 8))) {
      throw new IllegalArgumentException("Unexpected FIX begin string");
    }
    return message;
  }

  public void validateForEnvelope(EventEnvelope event, String wireMessage) {
    Message parsed = parseAndValidate(wireMessage);
    var header = parsed.getHeader();
    if (!required(event.getSenderOrganizationReference(), "sender")
            .equals(string(header, 49))
        || !required(event.getReceiverOrganizationReference(), "receiver")
            .equals(string(header, 56))
        || Integer.parseInt(string(header, 34))
            != Math.toIntExact(event.getAggregateVersion() + 1)
        || !SENDING_TIME.format(event.getOccurredAt()).equals(string(header, 52))
        || !event.getAggregateReference().equals(string(parsed, 9001))
        || !event.getCorrelationId().equals(string(parsed, 9001))) {
      throw new IllegalArgumentException("FIX U1 fields do not match Kafka envelope metadata");
    }
  }

  public void validateCollateralInquiryForEnvelope(EventEnvelope event, String wireMessage) {
    Message parsed = parseCollateralInquiryAndValidate(wireMessage);
    var header = parsed.getHeader();
    String inquiryId = string(parsed, 909);
    if (!required(event.getSenderOrganizationReference(), "sender")
            .equals(string(header, 49))
        || !required(event.getReceiverOrganizationReference(), "receiver")
            .equals(string(header, 56))
        || Integer.parseInt(string(header, 34))
            != Math.toIntExact(event.getAggregateVersion() + 1)
        || !SENDING_TIME.format(event.getOccurredAt()).equals(string(header, 52))
        || !inquiryId.equals(event.getAggregateReference())
        || !inquiryId.equals(event.getCorrelationId())) {
      throw new IllegalArgumentException("FIX BB fields do not match Kafka envelope metadata");
    }
  }

  private static void setHeader(Message message, EventEnvelope event, String messageType) {
    var header = message.getHeader();
    header.setString(8, BEGIN_STRING);
    header.setString(35, messageType);
    header.setString(49, required(event.getSenderOrganizationReference(), "sender"));
    header.setString(56, required(event.getReceiverOrganizationReference(), "receiver"));
    header.setInt(34, Math.toIntExact(event.getAggregateVersion() + 1));
    header.setString(52, SENDING_TIME.format(event.getOccurredAt()));
  }

  private static void validateFrame(String wireMessage) {
    if (wireMessage == null || !wireMessage.startsWith("8=FIX.4.4" + SOH + "9=")) {
      throw new IllegalArgumentException("Invalid FIX begin string or body length");
    }
    for (int i = 0; i < wireMessage.length(); i++) {
      if (wireMessage.charAt(i) > 0x7f) {
        throw new IllegalArgumentException("FIX 4.4 payload must be ASCII");
      }
    }
    byte[] bytes = wireMessage.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    int bodyLengthSeparator = wireMessage.indexOf(SOH, 10);
    int checksumTag = wireMessage.lastIndexOf(SOH + "10=");
    if (bodyLengthSeparator < 0
        || checksumTag < bodyLengthSeparator
        || !wireMessage.endsWith(SOH + "")) {
      throw new IllegalArgumentException("Malformed FIX BodyLength or CheckSum framing");
    }
    int bodyStart = bodyLengthSeparator + 1;
    int checksumStart = checksumTag + 1;
    int declaredBodyLength;
    try {
      declaredBodyLength =
          Integer.parseInt(wireMessage.substring("8=FIX.4.4".length() + 3, bodyLengthSeparator));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid FIX BodyLength", exception);
    }
    if (declaredBodyLength != checksumStart - bodyStart) {
      throw new IllegalArgumentException("FIX BodyLength does not match message body");
    }
    int checksumValueStart = checksumStart + 3;
    if (wireMessage.length() != checksumValueStart + 4) {
      throw new IllegalArgumentException("Malformed FIX CheckSum field");
    }
    String declaredChecksum = wireMessage.substring(checksumValueStart, checksumValueStart + 3);
    if (!declaredChecksum.matches("[0-9]{3}")) {
      throw new IllegalArgumentException("Invalid FIX CheckSum value");
    }
    int sum = 0;
    for (int i = 0; i < checksumStart; i++) {
      sum += bytes[i] & 0xff;
    }
    if (Integer.parseInt(declaredChecksum) != sum % 256) {
      throw new IllegalArgumentException("FIX CheckSum does not match message");
    }
  }

  private static String string(FieldMap fields, int tag) {
    try {
      return fields.getString(tag);
    } catch (quickfix.FieldNotFound exception) {
      throw new IllegalArgumentException("Required FIX field is missing: " + tag, exception);
    }
  }

  private static int integer(FieldMap fields, int tag) {
    try {
      return fields.getInt(tag);
    } catch (quickfix.FieldNotFound | NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid FIX field: " + tag, exception);
    }
  }

  private static long longValue(FieldMap fields, int tag) {
    try {
      return new BigDecimal(fields.getString(tag)).longValueExact();
    } catch (quickfix.FieldNotFound | ArithmeticException | NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid FIX integer field: " + tag, exception);
    }
  }

  private static BigDecimal decimal(FieldMap fields, int tag) {
    try {
      return new BigDecimal(fields.getString(tag));
    } catch (quickfix.FieldNotFound | NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid FIX decimal field: " + tag, exception);
    }
  }

  private static Instant timestamp(String value) {
    try {
      return Instant.parse(value);
    } catch (java.time.format.DateTimeParseException exception) {
      throw new IllegalArgumentException("Invalid FIX timestamp", exception);
    }
  }

  private static void requireUuid(String value, String name) {
    if (value == null
        || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
      throw new IllegalArgumentException("Invalid FIX " + name);
    }
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing FIX " + name);
    }
    return value;
  }

  private static String text(Object value, String name) {
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalArgumentException("Missing or invalid FIX " + name);
    }
    return text;
  }

  private static String uuid(String value) {
    if (value == null
        || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
      throw new IllegalArgumentException("Invalid collateral inquiry ID");
    }
    return value;
  }

  private static String quantity(Object value) {
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException("Invalid FIX quantity");
    }
    try {
      long quantity = new BigDecimal(number.toString()).longValueExact();
      if (quantity <= 0) throw new IllegalArgumentException("FIX quantity must be positive");
      return Long.toString(quantity);
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new IllegalArgumentException("FIX quantity must be a positive whole number", exception);
    }
  }

  private static String date(Object value) {
    if (value instanceof LocalDate date) return SETTLEMENT_DATE.format(date);
    if (value instanceof String text) return SETTLEMENT_DATE.format(LocalDate.parse(text));
    throw new IllegalArgumentException("Invalid FIX settlement date");
  }

  private static int number(Object value) {
    if (value instanceof Number number) return number.intValue();
    return -1;
  }
}
