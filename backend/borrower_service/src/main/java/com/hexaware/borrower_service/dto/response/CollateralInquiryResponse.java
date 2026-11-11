package com.hexaware.borrower_service.dto.response;

public record CollateralInquiryResponse(
    String inquiryId, String requestReference, String mslaReference, String status) {}
