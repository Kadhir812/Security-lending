package com.hexaware.custodian_service.client;

import java.util.List;

public record SecurityPricePageResponse(List<SecurityPriceResponse> content, int page, int size, long totalElements, int totalPages) {}
