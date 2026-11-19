package com.hexaware.borrower_service.dto.request;

import com.hexaware.borrower_service.enums.BorrowerTradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record TradeExecutionRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}") String tradeReference,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{1,80}") String transactionReference,
    @NotNull BorrowerTradeType tradeType,
    @NotNull @Positive Long quantity,
    @NotNull @DecimalMin("0.000001") @Digits(integer = 14, fraction = 6) BigDecimal price) {}
