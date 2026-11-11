package com.hexaware.custodian_service.dto.response;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.enums.OrganizationStatus;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrganizationResponse {
 private String organizationReference; private String organizationName; private OrganizationType organizationType; private OrganizationStatus status;
 private Long version; private Instant createdAt; private Long createdByUserId; private Instant updatedAt; private Long updatedByUserId;
}
