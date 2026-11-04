package com.hexaware.valuation_accural.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(CalculationException.class)
  ResponseEntity<ProblemDetail> calculation(CalculationException exception) {
    return ResponseEntity.status(exception.getStatus())
        .body(ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage()));
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
