package com.hexaware.borrower_service.client;

import java.util.List;

public record BorrowerSecurityPricePage(
    List<BorrowerSecurityPrice> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {}
