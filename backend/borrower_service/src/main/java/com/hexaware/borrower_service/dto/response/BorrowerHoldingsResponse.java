package com.hexaware.borrower_service.dto.response;

import java.util.List;

public record BorrowerHoldingsResponse(int securityCount, long borrowedContractCount, List<BorrowerHoldingPosition> positions) {}
