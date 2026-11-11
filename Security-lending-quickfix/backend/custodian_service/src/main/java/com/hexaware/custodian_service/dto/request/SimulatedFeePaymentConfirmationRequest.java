package com.hexaware.custodian_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulatedFeePaymentConfirmationRequest {
  @NotBlank
  @Size(max = 150)
  @Pattern(regexp = "[A-Za-z0-9._:-]{1,150}")
  private String externalConfirmationReference;
}
