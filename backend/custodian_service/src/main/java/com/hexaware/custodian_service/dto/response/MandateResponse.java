package com.hexaware.custodian_service.dto.response;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;
import com.hexaware.custodian_service.enums.MandateStatus;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MandateResponse {
 private String mandateReference; private String lenderOrganizationReference; private String custodianOrganizationReference;
 private LocalDate effectiveFrom; private LocalDate effectiveTo; private MandateStatus status; private boolean currentlyEffective;
 private Long version; private Instant createdAt; private Long createdByUserId; private Instant updatedAt; private Long updatedByUserId;
}
