package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoanRequestRejection(@NotBlank @Size(max = 500) String reason) {}
