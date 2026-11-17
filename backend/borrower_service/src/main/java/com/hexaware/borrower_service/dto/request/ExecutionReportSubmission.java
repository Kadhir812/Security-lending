package com.hexaware.borrower_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.Instant;
import jakarta.validation.constraints.Digits;

public record ExecutionReportSubmission(
    @NotBlank @Size(max = 80) String tradeReference,
    @NotBlank @Size(max = 80) String orderReference,
    @NotNull @Positive Long quantity,
    @NotNull @DecimalMin("0.000001") @Digits(integer = 14, fraction = 6) BigDecimal price,
    @Digits(integer = 18, fraction = 2) BigDecimal realizedProfitLoss,
    @NotNull @PastOrPresent Instant tradeTime) {}
