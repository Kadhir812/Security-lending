package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.client.SecurityReferenceClient;
import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.enums.OrganizationType;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.MandateAccessRepository;
import com.hexaware.custodian_service.repository.MslaAgreementRepository;
import com.hexaware.custodian_service.repository.OrganizationRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.setup.LendingSetupAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.mockito.Mockito;

class LendingSetupAccessTest {

  @Test
  void lenderWithoutAuthenticatedCustodiansCurrentMandateIsHidden() {
    var setup = Mockito.mock(SetupAccess.class);
    var organizations = Mockito.mock(OrganizationRepository.class);
    var mandates = Mockito.mock(MandateAccessRepository.class);
    var agreements = Mockito.mock(MslaAgreementRepository.class);
    var securities = Mockito.mock(SecurityReferenceClient.class);
    var clock = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC);
    var access =
        new LendingSetupAccess(setup, organizations, mandates, agreements, securities, clock);
    var authentication = Mockito.mock(Authentication.class);
    var custodian =
        Organization.builder()
            .organizationId(50L)
            .organizationReference("CUSTODIAN-A")
            .organizationType(OrganizationType.CUSTODIAN)
            .build();
    var lender =
        Organization.builder()
            .organizationId(80L)
            .organizationReference("LENDER-A")
            .organizationType(OrganizationType.LENDER)
            .build();
    when(setup.actor(authentication, false)).thenReturn(custodian);
    when(organizations.findByOrganizationReference("LENDER-A")).thenReturn(java.util.Optional.of(lender));
    when(mandates.hasCurrentLenderMandate(
            "LENDER-A", 50L, java.time.LocalDate.of(2026, 2, 1), false))
        .thenReturn(false);

    assertThatThrownBy(() -> access.requireLender(authentication, "LENDER-A", false))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("mandate scope");
    verify(mandates).hasCurrentLenderMandate(
        "LENDER-A", 50L, java.time.LocalDate.of(2026, 2, 1), false);
  }
}
