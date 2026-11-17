package com.hexaware.security_master_market;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexaware.security_master_market.controller.SecurityMasterController;
import com.hexaware.security_master_market.controller.SecurityPriceController;
import com.hexaware.security_master_market.controller.MarketStreamController;
import com.hexaware.security_master_market.config.SchedulingAndCorsConfig;
import com.hexaware.security_master_market.dto.response.SecurityResponse;
import com.hexaware.security_master_market.enums.SecurityStatus;
import com.hexaware.security_master_market.enums.SecurityType;
import com.hexaware.security_master_market.repository.SecurityMasterRepository;
import com.hexaware.security_master_market.security.config.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.hexaware.security_master_market.service.SecurityMasterService;
import com.hexaware.security_master_market.service.SecurityPriceService;
import com.hexaware.security_master_market.service.YahooMarketFeedService;

@SpringJUnitConfig(SecurityHttpTest.Config.class)
@WebAppConfiguration
class SecurityHttpTest {
  @TestConfiguration
  @EnableWebMvc
  @Import({
    SecurityConfiguration.class,
    SecurityMasterController.class,
    SecurityPriceController.class,
    MarketStreamController.class,
    SchedulingAndCorsConfig.class
  })
  static class Config {}

  @Autowired WebApplicationContext context;
  @MockitoBean SecurityMasterService securities;
  @MockitoBean SecurityMasterRepository securityMasters;
  @MockitoBean SecurityPriceService prices;
  @MockitoBean YahooMarketFeedService marketFeed;
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void publicSecurityApiNeedsNoToken() throws Exception {
    mvc.perform(get("/api/securities")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/securities")).andExpect(status().isOk());
  }

  @Test
  void legacySecurityLookupKeepsCustodianResponseContract() throws Exception {
    when(securities.details("INE144J01027"))
        .thenReturn(
            SecurityResponse.builder()
                .securityId(42L)
                .version(3L)
                .isin("INE144J01027")
                .symbol("TEST")
                .securityName("Test Security")
                .securityType(SecurityType.EQUITY)
                .currency("INR")
                .exchangeName("NSE")
                .sedol("6205122")
                .sector("IT Services")
                .lotSize(1L)
                .status(SecurityStatus.ACTIVE)
                .build());

    mvc.perform(get("/api/securities/{isin}", "INE144J01027"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.securityId").value(42))
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(jsonPath("$.isin").value("INE144J01027"))
        .andExpect(jsonPath("$.symbol").value("TEST"))
        .andExpect(jsonPath("$.securityName").value("Test Security"))
        .andExpect(jsonPath("$.securityType").value("EQUITY"))
        .andExpect(jsonPath("$.currency").value("INR"))
        .andExpect(jsonPath("$.exchangeName").value("NSE"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.id").doesNotExist())
        .andExpect(jsonPath("$.sedol").doesNotExist());
  }

  @Test
  void versionedSecurityDetailsExposeFixedMetadata() throws Exception {
    when(securities.details("INE144J01027"))
        .thenReturn(
            SecurityResponse.builder()
                .securityId(42L)
                .version(3L)
                .isin("INE144J01027")
                .symbol("TEST")
                .securityName("Test Security")
                .securityType(SecurityType.EQUITY)
                .currency("INR")
                .exchangeName("NSE")
                .sedol("6205122")
                .sector("IT Services")
                .lotSize(1L)
                .status(SecurityStatus.ACTIVE)
                .build());

    mvc.perform(get("/api/v1/securities/{isin}", "INE144J01027"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sedol").value("6205122"))
        .andExpect(jsonPath("$.sector").value("IT Services"))
        .andExpect(jsonPath("$.lotSize").value(1));
  }

  @Test
  void versionedPriceAndMarketStreamRoutesArePublic() throws Exception {
    mvc.perform(get("/api/v1/securities/INE144J01027/prices"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/market-stream/snapshot"))
        .andExpect(status().isOk());
  }

  @Test
  void securityMasterCorsAllowsSecurityMasterUi() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(
                    "/api/v1/securities")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void noLoginPageOrExposedMetrics() throws Exception {
    mvc.perform(get("/login")).andExpect(status().isNotFound());
    mvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
  }
}
