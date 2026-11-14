package com.hexaware.security_master_market.exception;

import jakarta.persistence.OptimisticLockException;
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
  @ExceptionHandler(MarketDataException.class)
  ProblemDetail marketData(MarketDataException exception) {
    return ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
  }

  @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
  ProblemDetail outdated() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Outdated security version");
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    HandlerMethodValidationException.class
  })
  ProblemDetail malformed() {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, "Invalid request fields or parameters");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception exception) {
    if (exception instanceof ErrorResponse response) {
      return ProblemDetail.forStatusAndDetail(
          response.getStatusCode(), "Request could not be completed");
    }
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Request could not be completed");
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
  ProblemDetail conflict() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Persistence constraint violated");
  }
}
