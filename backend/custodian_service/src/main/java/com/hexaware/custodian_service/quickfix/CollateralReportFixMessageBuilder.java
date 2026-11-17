package com.hexaware.custodian_service.quickfix;

import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
public class CollateralReportFixMessageBuilder {
  private static final String BEGIN_STRING = "FIX.4.4";
  private static final char SOH = '\u0001';
  private static final DateTimeFormatter SENDING_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

  private final DataDictionary dictionary;

  public CollateralReportFixMessageBuilder() {
    try (var input = new ClassPathResource("quickfix/FIX44_SL.xml").getInputStream()) {
      dictionary = new DataDictionary(input);
    } catch (IOException | ConfigError exception) {
      throw new IllegalStateException("Unable to load FIX 4.4 collateral-report dictionary", exception);
    }
  }

  public String build(
      EventEnvelope event,
      String reportId,
      String inquiryId,
      String requestReference,
      String mslaReference,
      String currency,
      BigDecimal cashHaircutPercentage,
      BigDecimal coveragePercentage) {
    Message message = new Message();
    var header = message.getHeader();
    header.setString(8, BEGIN_STRING);
    header.setString(35, "BA");
    header.setString(49, event.getSenderOrganizationReference());
    header.setString(56, event.getReceiverOrganizationReference());
    header.setInt(34, Math.toIntExact(event.getAggregateVersion() + 1));
    header.setString(52, SENDING_TIME.format(event.getOccurredAt()));

    message.setString(908, uuid(reportId));
    message.setString(909, uuid(inquiryId));
    message.setInt(910, 0);
    message.setString(918, required(currency, "agreement currency"));
    message.setString(9001, required(requestReference, "request reference"));
    message.setString(9002, required(mslaReference, "MSLA reference"));
    message.setString(9010, "CASH");
    message.setString(
        9011,
        percentage(
            cashHaircutPercentage, "cash haircut", BigDecimal.ZERO, BigDecimal.valueOf(100)));
    message.setString(
        9012,
        percentage(
            coveragePercentage,
            "collateral coverage",
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(100_000)));

    String wireMessage = message.toString();
    validate(event, wireMessage);
    return wireMessage;
  }

  public void validate(EventEnvelope event, String wireMessage) {
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
      throw new IllegalArgumentException("Invalid FIX 4.4 BA collateral report", exception);
    }
    if (!BEGIN_STRING.equals(string(message.getHeader(), 8))
        || !"BA".equals(string(message.getHeader(), 35))
        || !event.getSenderOrganizationReference().equals(string(message.getHeader(), 49))
        || !event.getReceiverOrganizationReference().equals(string(message.getHeader(), 56))
        || Math.toIntExact(event.getAggregateVersion() + 1)
            != Integer.parseInt(string(message.getHeader(), 34))
        || !SENDING_TIME.format(event.getOccurredAt()).equals(string(message.getHeader(), 52))
        || !event.getCorrelationId().equals(string(message, 909))
        || !event.getAggregateReference().equals(string(message, 9001))) {
      throw new IllegalArgumentException("FIX BA fields do not match Kafka envelope metadata");
    }
  }

  private static String string(quickfix.FieldMap fields, int tag) {
    try {
      return fields.getString(tag);
    } catch (quickfix.FieldNotFound exception) {
      throw new IllegalArgumentException("Required FIX field is missing: " + tag, exception);
    }
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing FIX " + name);
    }
    return value;
  }

  private static String uuid(String value) {
    if (value == null
        || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
      throw new IllegalArgumentException("Invalid FIX collateral report identifier");
    }
    return value;
  }

  private static String percentage(
      BigDecimal value, String name, BigDecimal minimum, BigDecimal exclusiveMaximum) {
    if (value == null
        || value.compareTo(minimum) < 0
        || value.compareTo(exclusiveMaximum) >= 0) {
      throw new IllegalArgumentException("Invalid FIX " + name + " percentage");
    }
    return value.stripTrailingZeros().toPlainString();
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
}
