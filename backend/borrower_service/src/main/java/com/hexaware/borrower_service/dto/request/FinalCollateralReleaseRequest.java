package com.hexaware.borrower_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FinalCollateralReleaseRequest(
    @NotBlank @Size(max = 48) @Pattern(regexp = "[A-Za-z0-9._:-]+") String releaseReference) {}
