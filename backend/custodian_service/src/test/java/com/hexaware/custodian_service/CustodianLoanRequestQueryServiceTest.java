package com.hexaware.custodian_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hexaware.custodian_service.entity.Organization;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LoanRequestRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import com.hexaware.custodian_service.service.lending.CustodianLoanRequestQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class CustodianLoanRequestQueryServiceTest {
  @Mock SetupAccess access;
  @Mock LoanRequestRepository requests;
  @Mock Authentication authentication;

  private CustodianLoanRequestQueryService service;

  @BeforeEach
  void setUp() {
    service = new CustodianLoanRequestQueryService(access, requests);
  }

  @Test
  void listsOnlyRequestsScopedToAuthenticatedCustodian() {
    when(access.actor(authentication, false))
        .thenReturn(Organization.builder().organizationId(15L).build());
    when(requests.findReceivedByCustodian(15L, PageRequest.of(0, 20)))
        .thenReturn(Page.empty(PageRequest.of(0, 20)));

    var result = service.list(authentication, 0, 20);

    assertThat(result.getContent()).isEmpty();
    verify(requests).findReceivedByCustodian(15L, PageRequest.of(0, 20));
  }

  @Test
  void hidesRequestsNotReceivedByAuthenticatedCustodian() {
    when(access.actor(authentication, false))
        .thenReturn(Organization.builder().organizationId(15L).build());
    when(requests.findReceivedByCustodianAndReference(15L, "LR-OTHER"))
        .thenReturn(java.util.Optional.empty());

    assertThatThrownBy(() -> service.get(authentication, "LR-OTHER"))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void rejectsInvalidPagingBeforeRepositoryAccess() {
    when(access.actor(authentication, false))
        .thenReturn(Organization.builder().organizationId(15L).build());

    assertThatThrownBy(() -> service.list(authentication, 0, 101))
        .isInstanceOf(SetupException.class)
        .hasMessageContaining("page or size");

    verifyNoInteractions(requests);
  }
}
