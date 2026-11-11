package com.hexaware.custodian_service.quickfix;

import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.ValidationSettings;

@Component
public class LoanApprovalFixMessageBuilder {
  private static final String BEGIN_STRING = "FIX.4.4";
  private static final char SOH = '\u0001';
  private static final DateTimeFormatter FIX_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter FIX_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final DataDictionary dictionary;

  public LoanApprovalFixMessageBuilder() {
    try (var input = new ClassPathResource("quickfix/FIX44_SL.xml").getInputStream()) {
      dictionary = new DataDictionary(input);
    } catch (IOException | ConfigError exception) {
      throw new IllegalStateException("Unable to load FIX 4.4 approval dictionary", exception);
    }
  }

  public String build(
      EventEnvelope event, String mslaReference, Map<String, Object> decisionPayload) {
    if (!"APPROVED".equals(decisionPayload.get("decisionStatus"))) {
      throw new IllegalArgumentException("FIX AK is only for approved loan decisions");
    }
    Map<String, ?> contract = map(decisionPayload.get("contract"), "contract");
    Map<String, ?> terms = map(contract.get("collateralTerms"), "collateralTerms");
    Map<String, ?> valuation = map(contract.get("acceptedValuationSnapshot"), "valuation snapshot");

    Message message = new Message();
    var header = message.getHeader();
    header.setString(8, BEGIN_STRING);
    header.setString(35, "AK");
    header.setString(49, required(event.getSenderOrganizationReference(), "sender"));
    header.setString(56, required(event.getReceiverOrganizationReference(), "receiver"));
    header.setInt(34, Math.toIntExact(event.getAggregateVersion() + 1));
    header.setString(52, FIX_TIME.format(event.getOccurredAt()));

    message.setString(664, text(contract.get("transactionReference"), "transactionReference"));
    message.setInt(666, 0);
    message.setInt(773, 2);
    message.setInt(665, 4);
    message.setString(60, FIX_TIME.format(instant(decisionPayload.get("respondedAt"), "respondedAt")));
    message.setString(75, date(contract.get("tradeDate"), "tradeDate"));
    message.setString(9001, text(decisionPayload.get("requestReference"), "requestReference"));
    message.setString(9002, required(mslaReference, "MSLA reference"));

    message.setString(9100, decimal(decisionPayload.get("decisionVersion"), "decisionVersion"));
    message.setString(9101, "APPROVED");
    message.setString(9102, text(contract.get("securityIsin"), "securityIsin"));
    message.setString(9103, decimal(contract.get("quantity"), "quantity"));
    message.setString(9104, text(contract.get("settlementCurrency"), "settlementCurrency"));
    message.setString(9105, decimal(contract.get("agreedAnnualFeePct"), "agreedAnnualFeePct"));
    message.setString(9106, text(contract.get("dayCountBasis"), "dayCountBasis"));
    message.setString(9107, decimal(contract.get("collateralCoveragePct"), "collateralCoveragePct"));
    message.setString(9108, text(terms.get("collateralType"), "collateralType"));
    message.setString(9109, text(terms.get("currency"), "collateral currency"));
    message.setString(9110, decimal(terms.get("haircutId"), "haircutId"));
    message.setString(9111, decimal(terms.get("haircutPercentage"), "haircutPercentage"));
    message.setString(9112, decimal(terms.get("coveragePercentage"), "terms coveragePercentage"));
    message.setString(9113, decimal(terms.get("requiredAmount"), "terms requiredAmount"));
    message.setString(9114, decimal(terms.get("loanMarketValue"), "terms loanMarketValue"));
    message.setString(9115, text(terms.get("valuationReference"), "valuationReference"));

    message.setString(9116, text(valuation.get("calculationReference"), "calculationReference"));
    message.setString(9117, text(valuation.get("securityIsin"), "valuation securityIsin"));
    message.setString(9118, decimal(valuation.get("quantity"), "valuation quantity"));
    message.setString(9119, decimal(valuation.get("marketPrice"), "marketPrice"));
    message.setString(9120, text(valuation.get("currency"), "valuation currency"));
    message.setString(9121, text(valuation.get("priceReference"), "priceReference"));
    message.setString(9122, text(valuation.get("priceSource"), "priceSource"));
    message.setString(9123, instant(valuation.get("priceTimestamp"), "priceTimestamp").toString());
    message.setString(9124, instant(valuation.get("calculatedAt"), "calculatedAt").toString());
    message.setString(9125, decimal(valuation.get("loanMarketValue"), "valuation loanMarketValue"));
    message.setString(
        9126, decimal(valuation.get("requiredCashCollateral"), "valuation requiredCashCollateral"));
    message.setString(9127, decimal(valuation.get("coveragePercentage"), "valuation coveragePercentage"));
    message.setString(
        9128, decimal(valuation.get("cashHaircutPercentage"), "cashHaircutPercentage"));
    message.setString(
        9129, decimal(valuation.get("creditReservationAmount"), "creditReservationAmount"));
    message.setString(9130, decimal(contract.get("loanMarketValue"), "loanMarketValue"));
    message.setString(9131, decimal(contract.get("requiredCashCollateral"), "requiredCashCollateral"));
    message.setString(
        9132, text(contract.get("settlementCalendarVersion"), "settlementCalendarVersion"));
    message.setString(9133, date(contract.get("settlementDate"), "settlementDate"));
    message.setString(9134, required(event.getEventId(), "event ID"));
    message.setString(
        9135, instant(decisionPayload.get("respondedAt"), "respondedAt").toString());

    String wireMessage = message.toString();
    validate(event, wireMessage);
    return wireMessage;
  }

  public void validate(EventEnvelope event, String wireMessage) {
    validateFrame(wireMessage);
    Message message = new Message();
    try {
      ValidationSettings settings = new ValidationSettings();
      message.fromString(wireMessage, dictionary, settings, true);
      dictionary.validate(message, settings);
    } catch (InvalidMessage
        | FieldException
        | IncorrectDataFormat
        | IncorrectTagValue
        | quickfix.FieldNotFound exception) {
      throw new IllegalArgumentException("Invalid FIX 4.4 AK approval confirmation", exception);
    }
    if (!BEGIN_STRING.equals(string(message.getHeader(), 8))
        || !"AK".equals(string(message.getHeader(), 35))
        || !required(event.getSenderOrganizationReference(), "sender")
            .equals(string(message.getHeader(), 49))
        || !required(event.getReceiverOrganizationReference(), "receiver")
            .equals(string(message.getHeader(), 56))
        || Integer.parseInt(string(message.getHeader(), 34))
            != Math.toIntExact(event.getAggregateVersion() + 1)
        || !FIX_TIME.format(event.getOccurredAt()).equals(string(message.getHeader(), 52))
        || !event.getEventId().equals(string(message, 9134))
        || !event.getAggregateReference().equals(string(message, 9001))
        || !event.getCorrelationId().equals(string(message, 9001))
        || !Long.toString(event.getAggregateVersion()).equals(string(message, 9100))
        || !"APPROVED".equals(string(message, 9101))
        || integer(message, 666) != 0
        || integer(message, 773) != 2
        || integer(message, 665) != 4) {
      throw new IllegalArgumentException("FIX AK fields do not match decision envelope");
    }
  }

  private static Map<String, ?> map(Object value, String name) {
    if (!(value instanceof Map<?, ?> source)) {
      throw new IllegalArgumentException("Missing or invalid FIX " + name);
    }
    var result = new java.util.LinkedHashMap<String, Object>();
    source.forEach(
        (key, item) -> {
          if (!(key instanceof String field)) {
            throw new IllegalArgumentException("Invalid FIX " + name + " field");
          }
          result.put(field, item);
        });
    return result;
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
      return new BigDecimal(value.toString()).toPlainString();
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid FIX " + name, exception);
    }
  }

  private static Instant instant(Object value, String name) {
    try {
      if (value instanceof Instant instant) return instant;
      if (value instanceof String text) return Instant.parse(text);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid FIX " + name, exception);
    }
    throw new IllegalArgumentException("Missing or invalid FIX " + name);
  }

  private static String date(Object value, String name) {
    try {
      if (value instanceof LocalDate date) return FIX_DATE.format(date);
      if (value instanceof String text) return FIX_DATE.format(LocalDate.parse(text));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid FIX " + name, exception);
    }
    throw new IllegalArgumentException("Missing or invalid FIX " + name);
  }

  private static String string(quickfix.FieldMap fields, int tag) {
    try {
      return fields.getString(tag);
    } catch (quickfix.FieldNotFound exception) {
      throw new IllegalArgumentException("Missing FIX field " + tag, exception);
    }
  }

  private static int integer(quickfix.FieldMap fields, int tag) {
    try {
      return fields.getInt(tag);
    } catch (quickfix.FieldNotFound | NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid FIX field " + tag, exception);
    }
  }

  private static void validateFrame(String wireMessage) {
    if (wireMessage == null || !wireMessage.startsWith("8=FIX.4.4" + SOH + "9=")) {
      throw new IllegalArgumentException("Invalid FIX begin string or body length");
    }
    byte[] bytes = wireMessage.getBytes(StandardCharsets.US_ASCII);
    for (int i = 0; i < wireMessage.length(); i++) {
      if (wireMessage.charAt(i) > 0x7f) throw new IllegalArgumentException("FIX must be ASCII");
    }
    int bodyLengthSeparator = wireMessage.indexOf(SOH, 10);
    int checksumTag = wireMessage.lastIndexOf(SOH + "10=");
    if (bodyLengthSeparator < 0 || checksumTag < bodyLengthSeparator || !wireMessage.endsWith("" + SOH)) {
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
}
