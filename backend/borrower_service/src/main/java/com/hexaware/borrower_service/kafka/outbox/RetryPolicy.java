package com.hexaware.borrower_service.kafka.outbox;

import com.hexaware.borrower_service.kafka.config.MessagingProperties;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RetryPolicy {
  private final MessagingProperties properties;

  public boolean retryable(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof RetriableException
          || cause instanceof TimeoutException
          || cause instanceof InterruptedException) return true;
    }
    return false;
  }

  public long delayMs(int attempt) {
    long delay = properties.getInitialBackoffMs();
    for (int i = 1; i < attempt && delay < properties.getMaxBackoffMs(); i++) {
      delay = delay > properties.getMaxBackoffMs() / 2 ? properties.getMaxBackoffMs() : delay * 2;
    }
    return Math.min(delay, properties.getMaxBackoffMs());
  }

  public String safeFailure(Throwable failure) {
    return "DELIVERY_FAILED:" + failure.getClass().getSimpleName();
  }
}
