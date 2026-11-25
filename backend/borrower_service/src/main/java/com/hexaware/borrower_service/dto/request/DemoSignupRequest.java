package com.hexaware.borrower_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemoSignupRequest(
    @NotBlank @Size(min = 2, max = 100) String username,
    @NotBlank @Size(min = 2, max = 255) String organizationName,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 12, max = 72) String password) {}
