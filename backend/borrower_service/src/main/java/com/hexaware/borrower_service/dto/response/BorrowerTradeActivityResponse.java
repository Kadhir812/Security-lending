package com.hexaware.borrower_service.dto.response;

import com.hexaware.borrower_service.enums.BorrowerTradeType;
import java.math.BigDecimal;
import java.time.Instant;

public record BorrowerTradeActivityResponse(
    String tradeReference,
    String orderReference,
    String transactionReference,
    BorrowerTradeType tradeType,
    Long quantity,
    BigDecimal price,
    BigDecimal realizedProfitLoss,
    Instant tradeTime) {}
