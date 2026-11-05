package com.hexaware.borrower_service.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ReferenceClientConfiguration {
  @Bean
  RestClient custodianMslaRestClient(
      RestClient.Builder builder,
      @Value("${app.custodian.base-url:http://localhost:8081}") String baseUrl,
      @Value("${app.custodian.connect-timeout:PT2S}") Duration connectTimeout,
      @Value("${app.custodian.read-timeout:PT3S}") Duration readTimeout) {
    return client(builder, baseUrl, connectTimeout, readTimeout);
  }

  @Bean
  RestClient securityMarketRestClient(
      RestClient.Builder builder,
      @Value("${app.security-market.base-url:http://localhost:8080}") String baseUrl,
      @Value("${app.security-market.connect-timeout:PT2S}") Duration connectTimeout,
      @Value("${app.security-market.read-timeout:PT3S}") Duration readTimeout) {
    return client(builder, baseUrl, connectTimeout, readTimeout);
  }

  private RestClient client(
      RestClient.Builder builder, String baseUrl, Duration connectTimeout, Duration readTimeout) {
    var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
