package com.hexaware.security_master_market.service;

import com.hexaware.security_master_market.exception.MarketDataException;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Normalization shared by HTTP writes, searches and the opt-in seed loader. */
public final class MarketInput {
  private MarketInput() {}

  public static String text(String value, String field, int max) {
    if (value == null || value.isBlank() || value.strip().length() > max)
      throw MarketDataException.invalid(
          field + " is required and must be at most " + max + " characters");
    return value.strip();
  }

  public static String optional(String value, String field, int max) {
    return value == null || value.isBlank() ? null : text(value, field, max);
  }

  public static String upper(String value, String field, int max) {
    return text(text(value, field, max).toUpperCase(Locale.ROOT), field, max);
  }

  public static String currency(String value) {
    String result = upper(value, "currency", 3);
    try {
      Currency.getInstance(result);
    } catch (IllegalArgumentException e) {
      throw MarketDataException.invalid("Unsupported ISO currency");
    }
    return result;
  }

  public static String isin(String value) {
    String result = upper(value, "ISIN", 12);
    if (!result.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]"))
      throw MarketDataException.invalid("Invalid ISIN structure");
    StringBuilder digits = new StringBuilder();
    for (char c : result.toCharArray()) {
      if (c >= 'A' && c <= 'Z') digits.append(c - 'A' + 10);
      else digits.append(c);
    }
    int sum = 0;
    boolean doubled = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int n = digits.charAt(i) - '0';
      if (doubled) n *= 2;
      sum += n / 10 + n % 10;
      doubled = !doubled;
    }
    if (sum % 10 != 0) throw MarketDataException.invalid("Invalid ISIN check digit");
    return result;
  }

  public static Instant timestamp(Instant value) {
    if (value == null
        || value.isBefore(Instant.parse("1000-01-01T00:00:00Z"))
        || !value.isBefore(Instant.parse("9999-12-31T23:59:59.999999999Z"))
        || value.getNano() % 1000 != 0)
      throw MarketDataException.invalid(
          "Timestamp must fit MySQL DATETIME with at most six fractional digits");
    return value;
  }

  public static PageRequest page(int page, int size, Sort sort) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
      throw MarketDataException.invalid("page must be nonnegative and size between 1 and 100");
    return PageRequest.of(page, size, sort);
  }
}
