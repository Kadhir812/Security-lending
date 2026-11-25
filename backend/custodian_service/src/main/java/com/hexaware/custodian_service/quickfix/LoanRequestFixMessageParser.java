package com.hexaware.custodian_service.quickfix;

import com.hexaware.custodian_service.exception.InvalidEnvelopeException;
import com.hexaware.custodian_service.kafka.event.CollateralInquirySubmittedPayload;
import com.hexaware.custodian_service.kafka.event.EventEnvelope;
import com.hexaware.custodian_service.kafka.event.LoanRequestSubmittedPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
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
import tools.jackson.databind.ObjectMapper;

@Component
public class LoanRequestFixMessageParser {
  private static final String BEGIN_STRING = "FIX.4.4";
  private static final String MSG_TYPE = "U1";
  private static final char SOH = '\u0001';
  private static final DateTimeFormatter SENDING_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

  private final DataDictionary dictionary;
  private final ObjectMapper mapper;

  public LoanRequestFixMessageParser(ObjectMapper mapper) {
    this.mapper = mapper;
    try (var input = new ClassPathResource("quickfix/FIX44_SL.xml").getInputStream()) {
      dictionary = new DataDictionary(input);
    } catch (IOException | ConfigError exception) {
      throw new IllegalStateException("Unable to load FIX 4.4 loan-request dictionary", exception);
    }
  }

  public LoanRequestSubmittedPayload parse(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    if (!payload.containsKey("fixMessage")) {
      return legacyPayload(payload);
    }
    Object wireValue = payload.get("fixMessage");
    if (!(wireValue instanceof String wireMessage) || payload.size() != 1) {
      throw invalid("INVALID_FIX_U1_PAYLOAD");
    }
    Message message = parseMessage(wireMessage, MSG_TYPE);
    validateEnvelope(event, message);
    if (!"4".equals(string(message, 22))) {
      throw invalid("UNSUPPORTED_SECURITY_ID_SOURCE");
    }
    String isin = string(message, 48);
    if (!isin.matches("[A-Z0-9]{12}")) {
      throw invalid("INVALID_SECURITY_ISIN");
    }
    long quantity;
    try {
      String quantityText = string(message, 38);
      if (!quantityText.matches("[1-9][0-9]*")) throw invalid("INVALID_LOAN_QUANTITY");
      quantity = Long.parseLong(quantityText);
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_LOAN_QUANTITY");
    }
    LocalDate settlementDate;
    try {
      settlementDate = LocalDate.parse(string(message, 64), DateTimeFormatter.BASIC_ISO_DATE);
    } catch (RuntimeException exception) {
      throw invalid("INVALID_SETTLEMENT_DATE");
    }
    return new LoanRequestSubmittedPayload(
        1,
        string(message, 9001),
        string(message.getHeader(), 49),
        string(message.getHeader(), 56),
        string(message, 9002),
        optionalString(message, 9005),
        isin,
        quantity,
        settlementDate,
        string(message, 15),
        string(message, 9003),
        string(message, 9004));
  }

  public CollateralInquirySubmittedPayload parseCollateralInquiry(EventEnvelope event) {
    Map<String, Object> payload = event.getPayload();
    Object wireValue = payload.get("fixMessage");
    if (!(wireValue instanceof String wireMessage) || payload.size() != 1) {
      throw invalid("INVALID_FIX_BB_PAYLOAD");
    }
    Message message = parseMessage(wireMessage, "BB");
    validateCollateralInquiryEnvelope(event, message);
    String inquiryId = string(message, 909);
    String requestReference = string(message, 9001);
    String mslaReference = string(message, 9002);
    if (!inquiryId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        || !requestReference.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}")
        || mslaReference.isBlank()
        || mslaReference.length() > 80) {
      throw invalid("INVALID_FIX_BB_FIELDS");
    }
    return new CollateralInquirySubmittedPayload(
        inquiryId,
        requestReference,
        mslaReference,
        string(message.getHeader(), 49),
        string(message.getHeader(), 56));
  }

  private Message parseMessage(String wireMessage, String expectedType) {
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
      throw invalid("INVALID_FIX_U1_MESSAGE");
    }
    if (!BEGIN_STRING.equals(string(message.getHeader(), 8))
        || !expectedType.equals(string(message.getHeader(), 35))) {
      throw invalid("UNSUPPORTED_FIX_MESSAGE_TYPE");
    }
    return message;
  }

  private void validateCollateralInquiryEnvelope(EventEnvelope event, Message message) {
    var header = message.getHeader();
    String inquiryId = string(message, 909);
    if (!"BORROWER".equals(event.getProducerService())
        || !"CUSTODIAN".equals(event.getDestinationService())
        || !Objects.equals(event.getSenderOrganizationReference(), string(header, 49))
        || !Objects.equals(event.getReceiverOrganizationReference(), string(header, 56))
        || Math.toIntExact(event.getAggregateVersion() + 1) != integer(header, 34)
        || !SENDING_TIME.format(event.getOccurredAt()).equals(string(header, 52))
        || !inquiryId.equals(event.getAggregateReference())
        || !inquiryId.equals(event.getCorrelationId())) {
      throw invalid("FIX_BB_ENVELOPE_MISMATCH");
    }
  }

  private void validateEnvelope(EventEnvelope event, Message message) {
    var header = message.getHeader();
    if (!"BORROWER".equals(event.getProducerService())
        || !"CUSTODIAN".equals(event.getDestinationService())
        || !event.getSenderOrganizationReference().equals(string(header, 49))
        || !event.getReceiverOrganizationReference().equals(string(header, 56))
        || Math.toIntExact(event.getAggregateVersion() + 1)
            != integer(header, 34)
        || !SENDING_TIME.format(event.getOccurredAt()).equals(string(header, 52))
        || !event.getAggregateReference().equals(string(message, 9001))
        || !event.getCorrelationId().equals(string(message, 9001))) {
      throw invalid("FIX_U1_ENVELOPE_MISMATCH");
    }
  }

  private LoanRequestSubmittedPayload legacyPayload(Map<String, Object> payload) {
    try {
      LoanRequestSubmittedPayload legacy =
          mapper.convertValue(payload, LoanRequestSubmittedPayload.class);
      if (legacy == null || legacy.payloadVersion() == null || legacy.payloadVersion() != 1) {
        throw invalid("INVALID_LEGACY_LOAN_REQUEST_PAYLOAD");
      }
      return legacy;
    } catch (InvalidEnvelopeException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw invalid("INVALID_LEGACY_LOAN_REQUEST_PAYLOAD");
    }
  }

  private static void validateFrame(String wireMessage) {
    if (wireMessage == null || !wireMessage.startsWith("8=FIX.4.4" + SOH + "9=")) {
      throw invalid("INVALID_FIX_BEGIN_STRING_OR_BODY_LENGTH");
    }
    for (int i = 0; i < wireMessage.length(); i++) {
      if (wireMessage.charAt(i) > 0x7f) throw invalid("NON_ASCII_FIX_PAYLOAD");
    }
    byte[] bytes = wireMessage.getBytes(StandardCharsets.US_ASCII);
    int bodyLengthSeparator = wireMessage.indexOf(SOH, 10);
    int checksumTag = wireMessage.lastIndexOf(SOH + "10=");
    if (bodyLengthSeparator < 0 || checksumTag < bodyLengthSeparator || !wireMessage.endsWith(SOH + "")) {
      throw invalid("MALFORMED_FIX_FRAMING");
    }
    int bodyStart = bodyLengthSeparator + 1;
    int checksumStart = checksumTag + 1;
    int declaredBodyLength;
    try {
      declaredBodyLength =
          Integer.parseInt(wireMessage.substring("8=FIX.4.4".length() + 3, bodyLengthSeparator));
    } catch (NumberFormatException exception) {
      throw invalid("INVALID_FIX_BODY_LENGTH");
    }
    if (declaredBodyLength != checksumStart - bodyStart) {
      throw invalid("FIX_BODY_LENGTH_MISMATCH");
    }
    int checksumValueStart = checksumStart + 3;
    if (wireMessage.length() != checksumValueStart + 4) {
      throw invalid("MALFORMED_FIX_CHECKSUM");
    }
    String declaredChecksum = wireMessage.substring(checksumValueStart, checksumValueStart + 3);
    if (!declaredChecksum.matches("[0-9]{3}")) throw invalid("INVALID_FIX_CHECKSUM");
    int sum = 0;
    for (int i = 0; i < checksumStart; i++) sum += bytes[i] & 0xff;
    if (Integer.parseInt(declaredChecksum) != sum % 256) {
      throw invalid("FIX_CHECKSUM_MISMATCH");
    }
  }

  private static String string(FieldMap fields, int tag) {
    try {
      return fields.getString(tag);
    } catch (quickfix.FieldNotFound exception) {
      throw invalid("MISSING_FIX_FIELD_" + tag);
    }
  }

  private static String optionalString(FieldMap fields, int tag) {
    try {
      return fields.getString(tag);
    } catch (quickfix.FieldNotFound exception) {
      return null;
    }
  }

  private static int integer(FieldMap fields, int tag) {
    try {
      return fields.getInt(tag);
    } catch (quickfix.FieldNotFound | NumberFormatException exception) {
      throw invalid("INVALID_FIX_FIELD_" + tag);
    }
  }

  private static InvalidEnvelopeException invalid(String reason) {
    return new InvalidEnvelopeException(reason);
  }
}
