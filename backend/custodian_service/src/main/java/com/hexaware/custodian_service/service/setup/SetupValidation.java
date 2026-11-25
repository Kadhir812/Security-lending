package com.hexaware.custodian_service.service.setup;

import com.hexaware.custodian_service.exception.SetupException;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class SetupValidation {

  private SetupValidation() {}

  public static String text(String value, String field, int limit) {
    if (value == null || value.isBlank() || value.strip().length() > limit) {
      throw SetupException.invalid(
          field + " is required and must be at most " + limit + " characters");
    }
    return value.strip();
  }

  public static String reference(String value) {
    return text(value, "reference", 80);
  }

  public static String currency(String value) {
    String result = text(value, "baseCurrency", 3).toUpperCase(Locale.ROOT);
    try {
      Currency.getInstance(result);
    } catch (IllegalArgumentException exception) {
      throw SetupException.invalid("Unsupported ISO currency");
    }
    return result;
  }

  public static void dates(LocalDate from, LocalDate to) {
    boolean invalidStart = from == null || from.getYear() < 1000 || from.getYear() > 9999;
    boolean invalidEnd =
        from != null && to != null && (to.isBefore(from) || to.getYear() > 9999);
    if (invalidStart || invalidEnd) {
      throw SetupException.invalid("Invalid effective date range");
    }
  }

  public static boolean covers(LocalDate from, LocalDate to, LocalDate date) {
    return from != null
        && !date.isBefore(from)
        && (to == null || !date.isAfter(to));
  }

  public static void version(Long actual, Long requested) {
    if (requested == null || !Objects.equals(actual, requested)) {
      throw SetupException.conflict("Version is required and must match; reload the resource");
    }
  }

  public static void newVersion(Long version) {
    if (version != null) {
      throw SetupException.invalid("Do not supply version when creating a resource");
    }
  }

  public static PageRequest page(int page, int size, String field) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
      throw SetupException.invalid("page must be nonnegative and size between 1 and 100");
    }
    return PageRequest.of(page, size, Sort.by(field));
  }
}
