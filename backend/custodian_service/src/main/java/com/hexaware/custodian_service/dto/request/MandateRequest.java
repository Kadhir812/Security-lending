package com.hexaware.custodian_service.dto.request;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import com.hexaware.custodian_service.enums.MandateStatus;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MandateRequest {
 @NotBlank @Size(max=80) private String mandateReference;
 @NotBlank @Size(max=80) private String lenderOrganizationReference;
 @NotNull private LocalDate effectiveFrom;
 private LocalDate effectiveTo;
 @NotNull private MandateStatus status;
 @PositiveOrZero private Long version;
}
