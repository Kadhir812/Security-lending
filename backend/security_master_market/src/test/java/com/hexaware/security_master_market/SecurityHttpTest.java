package com.hexaware.security_master_market;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexaware.security_master_market.controller.SecurityMasterController;
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
import com.hexaware.security_master_market.service.SecurityMasterService;

@SpringJUnitConfig(SecurityHttpTest.Config.class)
@WebAppConfiguration
class SecurityHttpTest {
  @TestConfiguration
  @EnableWebMvc
  @Import({SecurityConfiguration.class, SecurityMasterController.class})
  static class Config {}

  @Autowired WebApplicationContext context;
  @MockitoBean SecurityMasterService securities;
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void publicSecurityApiNeedsNoToken() throws Exception {
    mvc.perform(get("/api/securities")).andExpect(status().isOk());
  }

  @Test
  void noLoginPageOrExposedMetrics() throws Exception {
    mvc.perform(get("/login")).andExpect(status().isNotFound());
    mvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
  }
}
