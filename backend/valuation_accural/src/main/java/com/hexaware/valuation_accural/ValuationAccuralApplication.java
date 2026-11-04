package com.hexaware.valuation_accural;

import com.hexaware.valuation_accural.config.LoanCalculationProperties;
import com.hexaware.valuation_accural.config.MarketDataProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigurationProperties({LoanCalculationProperties.class, MarketDataProperties.class})
public class ValuationAccuralApplication {

  public static void main(String[] args) {
    SpringApplication.run(ValuationAccuralApplication.class, args);
  }
}
