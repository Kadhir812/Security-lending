package com.hexaware.custodian_service.quickfix;

import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.FieldException;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.ValidationSettings;

@Component
public class SettlementStatusFixMessageBuilder {
  private static final String BEGIN_STRING = "FIX.4.4";
  private static final char SOH = '\u0001';
  private static final DateTimeFormatter FIX_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

  private final DataDictionary dictionary;

  public SettlementStatusFixMessageBuilder() {
    try (var input = new ClassPathResource("quickfix/FIX44_SL.xml").getInputStream()) {
      dictionary = new DataDictionary(input);
    } catch (IOException | ConfigError exception) {
      throw new IllegalStateException("Unable to load FIX 4.4 settlement dictionary", exception);
    }
  }

  public String build(EventEnvelope event, Map<String, Object> payload) {
    if (!"SETTLEMENT_INSTRUCTION_UPDATED".equals(event.getEventType())
        || !"CUSTODIAN".equals(event.getProducerService())
        || !"BORROWER".equals(event.getDestinationService())
        || event.getSchemaVersion() != 1
        || event.getOccurredAt() == null
        || !"SECURITY_DELIVERY".equals(payload.get("instructionType"))
        || !"SETTLED".equals(payload.get("settlementStatus"))
        || !"SETTLED".equals(payload.get("instructionStatus"))
        || !Boolean.TRUE.equals(payload.get("simulatedConfirmation"))
        || !Long.valueOf(1).equals(event.getAggregateVersion())
        || !Long.valueOf(1).equals(number(payload.get("aggregateVersion")))
        || !Long.valueOf(1).equals(number(payload.get("payloadVersion")))) {
      throw new IllegalArgumentException("FIX U2 is only for initial settled securities delivery");
    }

    Message message = new Message();
    var header = message.getHeader();
    header.setString(8, BEGIN_STRING);
    header.setString(35, "U2");
    header.setString(49, required(event.getSenderOrganizationReference(), "sender"));
    header.setString(56, required(event.getReceiverOrganizationReference(), "receiver"));
    header.setInt(34, Math.toIntExact(event.getAggregateVersion() + 1));
    header.setString(52, FIX_TIME.format(event.getOccurredAt()));

    String instructionReference = text(payload.get("instructionReference"), "instruction reference");
    if (!instructionReference.equals(event.getAggregateReference())
        || !instructionReference.equals(event.getCorrelationId())) {
      throw new IllegalArgumentException("FIX U2 instruction reference does not match envelope");
    }
    String currency = text(payload.get("currency"), "currency");
    String securityIsin = text(payload.get("securityIsin"), "security ISIN");
    if (!currency.matches("[A-Z]{3}") || !securityIsin.matches("[A-Z0-9]{12}")) {
      throw new IllegalArgumentException("Invalid FIX settlement currency or ISIN");
    }
    message.setString(15, currency);
    message.setString(9154, positiveWhole(payload.get("quantity"), "quantity"));
    message.setString(48, securityIsin);
    message.setString(22, "4");
    message.setString(60, FIX_TIME.format(instant(payload.get("settledAt"), "settledAt")));
    message.setString(9140, text(payload.get("transactionReference"), "transaction reference"));
    message.setString(9141, instructionReference);
    message.setString(9142, "SECURITY_DELIVERY");
    message.setString(9143, "SETTLED");
    message.setString(9144, text(payload.get("contractStatus"), "contract status"));
    message.setString(9145, positiveWhole(payload.get("contractVersion"), "contract version"));
    message.setString(9146, positiveWhole(payload.get("deliveredQuantity"), "delivered quantity"));
    message.setString(
        9147, positiveWhole(payload.get("outstandingQuantity"), "outstanding quantity"));
    message.setString(
        9148, decimal(payload.get("confirmedCashCollateral"), "confirmed CASH collateral"));
    message.setString(
        9149, text(payload.get("externalConfirmationReference"), "confirmation reference"));
    message.setString(9150, "Y");
    message.setString(9151, required(event.getEventId(), "event ID"));
    message.setString(9152, "1");
    Instant settledAt = instant(payload.get("settledAt"), "settledAt");
    if (settledAt.isAfter(event.getOccurredAt())) {
      throw new IllegalArgumentException("FIX settlement time is after the event time");
    }
    message.setString(9153, settledAt.toString());

    String wireMessage = message.toString();
    validate(event, wireMessage);
    return wireMessage;
  }

  private void validate(EventEnvelope event, String wireMessage) {
    validateFrame(wireMessage);
    Message parsed = new Message();
    try {
      ValidationSettings settings = new ValidationSettings();
      parsed.fromString(wireMessage, dictionary, settings, true);
      dictionary.validate(parsed, settings);
    } catch (InvalidMessage
        | FieldException
        | IncorrectDataFormat
        | IncorrectTagValue
        | quickfix.FieldNotFound exception) {
      throw new IllegalArgumentException("Invalid FIX 4.4 U2 settlement confirmation", exception);
    }
    if (!BEGIN_STRING.equals(string(parsed.getHeader(), 8))
        || !"U2".equals(string(parsed.getHeader(), 35))
        || !event.getSenderOrganizationReference().equals(string(parsed.getHeader(), 49))
        || !event.getReceiverOrganizationReference().equals(string(parsed.getHeader(), 56))
        || Math.toIntExact(event.getAggregateVersion() + 1)
            != Integer.parseInt(string(parsed.getHeader(), 34))
        || !FIX_TIME.format(event.getOccurredAt()).equals(string(parsed.getHeader(), 52))
        || !event.getEventId().equals(string(parsed, 9151))
        || !event.getAggregateReference().equals(string(parsed, 9141))
        || !event.getCorrelationId().equals(string(parsed, 9141))
        || !"1".equals(string(parsed, 9152))) {
      throw new IllegalArgumentException("FIX U2 fields do not match settlement envelope");
    }
  }

  private static void validateFrame(String wireMessage) {
    if (wireMessage == null || !wireMessage.startsWith("8=FIX.4.4" + SOH + "9=")) {
      throw new IllegalArgumentException("Invalid FIX begin string or body length");
    }
    byte[] bytes = wireMessage.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    for (int i = 0; i < wireMessage.length(); i++) {
      if (wireMessage.charAt(i) > 0x7f) throw new IllegalArgumentException("FIX must be ASCII");
    }
    int bodyLengthSeparator = wireMessage.indexOf(SOH, 10);
    int checksumTag = wireMessage.lastIndexOf(SOH + "10=");
    if (bodyLengthSeparator < 0
        || checksumTag < bodyLengthSeparator
        || !wireMessage.endsWith("" + SOH)) {
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
    for (int i = 0; i < checksumStart; i++) sum += bytes[i] & 0xff;
    if (Integer.parseInt(declaredChecksum) != sum % 256) {
      throw new IllegalArgumentException("FIX CheckSum does not match message");
    }
  }

  private static String string(quickfix.FieldMap fields, int tag) {
    try {
      return fields.getString(tag);
    } catch (quickfix.FieldNotFound exception) {
      throw new IllegalArgumentException("Missing FIX field " + tag, exception);
    }
  }

  private static String text(Object value, String name) {
    if (!(value instanceof String text) || text.isBlank() || text.indexOf(SOH) >= 0) {
      throw new IllegalArgumentException("Missing or invalid FIX " + name);
    }
    return text;
  }

  private static String required(String value, String name) {
    return text(value, name);
  }

  private static String decimal(Object value, String name) {
    if (!(value instanceof Number) && !(value instanceof String)) {
      throw new IllegalArgumentException("Missing or invalid FIX " + name);
    }
    try {
      BigDecimal decimal = new BigDecimal(value.toString());
      if (decimal.signum() < 0) throw new IllegalArgumentException("Invalid FIX " + name);
      return decimal.toPlainString();
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid FIX " + name, exception);
    }
  }

  private static String positiveWhole(Object value, String name) {
    long result;
    try {
      result = new BigDecimal(value.toString()).longValueExact();
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid FIX " + name, exception);
    }
    if (result <= 0) throw new IllegalArgumentException("Invalid FIX " + name);
    return Long.toString(result);
  }

  private static Instant instant(Object value, String name) {
    try {
      return value instanceof Instant instant ? instant : Instant.parse(value.toString());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid FIX " + name, exception);
    }
  }

  private static Long number(Object value) {
    try {
      if (value == null) throw new IllegalArgumentException("Missing FIX settlement version");
      return new BigDecimal(value.toString()).longValueExact();
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid FIX settlement version", exception);
    }
  }
}
