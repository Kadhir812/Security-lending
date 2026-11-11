package com.hexaware.custodian_service.dto.response;

public record InventoryResponse(
    String lenderOrganizationReference,
    String securityIsin,
    Long totalQuantity,
    Long onLoanQuantity,
    Long reservedQuantity,
    Long availableQuantity,
    Long version) {}
