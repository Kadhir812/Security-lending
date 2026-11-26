package com.hexaware.custodian_service.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(SetupException.class)
  ProblemDetail setup(SetupException exception) {
    logServiceFailure(exception.getStatus(), exception.getMessage());
    return ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
  }

  @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
  ProblemDetail outdated() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Outdated resource version");
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    ConstraintViolationException.class,
    HandlerMethodValidationException.class
  })
  ProblemDetail malformed() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Invalid request fields or parameters");
  }

  private void logServiceFailure(HttpStatus status, String detail) {
    if (status.is5xxServerError()) {
      log.warn(
          "Service request failed with HTTP {} [correlationId={}]: {}",
          status.value(),
          MDC.get("correlationId"),
          detail);
    }
  }

  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail forbidden() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalid() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail conflict(DataIntegrityViolationException exception) {
    String sqlState = findSqlState(exception);
    Integer mysqlErrorCode = findMysqlErrorCode(exception);
    String constraintName = findConstraintName(exception);
    String dbMessage = extractDatabaseMessage(exception);
    boolean foreignKeyViolation = isForeignKeyViolation(constraintName, sqlState, dbMessage);
    log.error(
        "Data integrity violation on persistence request: sqlState={}, mysqlErrorCode={}, constraintName={}, foreignKeyViolation={}, databaseMessage={}, liveDbCheck='organization_id and security_isin / referenced tables', rootCause={}",
        sqlState,
        mysqlErrorCode,
        constraintName,
        foreignKeyViolation,
        dbMessage,
        exception.getMostSpecificCause() != null ? exception.getMostSpecificCause() : exception,
        exception);
    String detail = foreignKeyViolation
        ? "Database foreign key mismatch: the lender organization reference, security ISIN, or referenced live DB row is invalid or missing. Check the custodian database schema and foreign-key references."
        : "Persistence constraint violated";
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
  }

  private String extractDatabaseMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        return current.getMessage();
      }
      current = current.getCause();
    }
    return null;
  }

  private boolean isForeignKeyViolation(String constraintName, String sqlState, String message) {
    String normalizedConstraint = constraintName == null ? "" : constraintName.toLowerCase();
    String normalizedMessage = message == null ? "" : message.toLowerCase();
    return normalizedConstraint.contains("fk_")
        || normalizedConstraint.contains("foreign_key")
        || normalizedMessage.contains("foreign key")
        || normalizedMessage.contains("cannot add or update a child row")
        || ("23000".equalsIgnoreCase(sqlState) && normalizedMessage.contains("foreign key constraint"));
  }

  private String findSqlState(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof java.sql.SQLException sqlException && sqlException.getSQLState() != null) {
        return sqlException.getSQLState();
      }
      if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolationException
          && constraintViolationException.getSQLState() != null) {
        return constraintViolationException.getSQLState();
      }
      current = current.getCause();
    }
    return null;
  }

  private Integer findMysqlErrorCode(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof java.sql.SQLException sqlException && sqlException.getErrorCode() != 0) {
        return sqlException.getErrorCode();
      }
      if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolationException
          && constraintViolationException.getErrorCode() != 0) {
        return constraintViolationException.getErrorCode();
      }
      current = current.getCause();
    }
    return null;
  }

  private String findConstraintName(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolationException
          && constraintViolationException.getConstraintName() != null) {
        return constraintViolationException.getConstraintName();
      }
      if (current instanceof java.sql.SQLException sqlException) {
        String message = sqlException.getMessage();
        if (message != null) {
          int start = message.indexOf("for key '");
          if (start >= 0) {
            int end = message.indexOf("'", start + 10);
            if (end > start + 10) {
              return message.substring(start + 10, end);
            }
          }
        }
      }
      current = current.getCause();
    }
    return null;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception exception) {
    if (exception instanceof ErrorResponse response) {
      if (response.getStatusCode().is5xxServerError()) {
        log.error(
            "Unhandled HTTP {} server error [correlationId={}]",
            response.getStatusCode().value(),
            MDC.get("correlationId"),
            exception);
      }
      return ProblemDetail.forStatusAndDetail(
          response.getStatusCode(), "Request could not be completed");
    }
    log.error(
        "Unhandled HTTP 500 server error [correlationId={}]",
        MDC.get("correlationId"),
        exception);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Request could not be completed");
  }
}
