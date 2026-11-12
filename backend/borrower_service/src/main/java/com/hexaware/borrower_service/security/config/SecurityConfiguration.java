package com.hexaware.borrower_service.security.config;

import com.hexaware.borrower_service.security.authentication.LocalUserJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean
  public SecurityFilterChain security(HttpSecurity http, LocalUserJwtConverter converter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .denyAll()
                        .requestMatchers("/api/auth/signup", "/api/auth/login")
                        .permitAll()
                        .anyRequest()
                    .hasAuthority("ROLE_TRADER"))
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
        .build();
  }
}
