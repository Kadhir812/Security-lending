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
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.OrganizationStatus;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganizationRequest {
 @NotBlank @Size(max=80) private String organizationReference;
 @NotBlank @Size(max=255) private String organizationName;
 @NotNull private OrganizationType organizationType;
 @NotNull private OrganizationStatus status;
 @PositiveOrZero private Long version;
}
