package com.hexaware.custodian_service.client;

import com.hexaware.custodian_service.enums.AssetType;

public record SecurityReferenceResponse(
    Long securityId,
    Long version,
    String isin,
    String symbol,
    String securityName,
    AssetType securityType,
    String currency,
    String exchangeName,
    String status) {}
