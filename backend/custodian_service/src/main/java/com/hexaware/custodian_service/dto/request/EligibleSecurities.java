package com.hexaware.custodian_service.dto.request;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hexaware.custodian_service.enums.AssetType;
/** SQL JSON object: {"allowed_types":["EQUITY","GOVT_BOND"]}. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EligibleSecurities {
 @JsonProperty("allowed_types") @NotEmpty private List<@NotNull AssetType> allowedTypes;
}
