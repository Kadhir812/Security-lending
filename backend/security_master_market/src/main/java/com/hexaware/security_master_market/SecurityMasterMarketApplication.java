package com.hexaware.security_master_market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SecurityMasterMarketApplication {

  public static void main(String[] args) {
    SpringApplication.run(SecurityMasterMarketApplication.class, args);
  }
}
