package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexaware.custodian_service.dto.request.EligibleSecurities;
import com.hexaware.custodian_service.enums.AssetType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class EligibleSecuritiesJsonTest {
  private final ObjectMapper json = JsonMapper.builder().build();

  @Test
  void allowedTypesUsesTheDatabaseJsonPropertyName() {
    var parsed =
        json.readValue(
            """
            {"allowed_types":["EQUITY","GOVT_BOND"]}
            """,
            EligibleSecurities.class);

    assertThat(parsed.getAllowedTypes()).containsExactly(AssetType.EQUITY, AssetType.GOVT_BOND);
    var serialized = json.readTree(json.writeValueAsString(parsed));
    assertThat(serialized.isObject()).isTrue();
    assertThat(serialized.path("allowed_types").isArray()).isTrue();
    assertThat(serialized.path("allowedTypes").isMissingNode()).isTrue();
  }
}
