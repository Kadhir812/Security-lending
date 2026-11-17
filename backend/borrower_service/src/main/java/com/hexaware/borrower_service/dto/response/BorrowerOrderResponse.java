package com.hexaware.borrower_service.dto.response;

import com.hexaware.borrower_service.enums.BorrowerOrderSide;
import com.hexaware.borrower_service.enums.BorrowerOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record BorrowerOrderResponse(
    String orderReference,
    String borrowerOrganizationReference,
    String securityIsin,
    String transactionReference,
    BorrowerOrderSide side,
    Long orderQuantity,
    Long filledQuantity,
    Long leavesQuantity,
    BigDecimal averageFillPrice,
    BorrowerOrderStatus orderStatus,
    Long version,
    Instant createdAt,
    Instant updatedAt) {}
