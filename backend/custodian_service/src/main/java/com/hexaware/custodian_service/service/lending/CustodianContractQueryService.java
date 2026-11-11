package com.hexaware.custodian_service.service.lending;

import com.hexaware.custodian_service.dto.response.PageResponse;
import com.hexaware.custodian_service.exception.SetupException;
import com.hexaware.custodian_service.repository.LoanContractRepository;
import com.hexaware.custodian_service.security.authorization.SetupAccess;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustodianContractQueryService {
  private final SetupAccess access;
  private final LoanContractRepository contracts;

  @Transactional(readOnly = true)
  public PageResponse<Map<String, Object>> list(Authentication authentication, int page, int size) {
    var custodian = access.actor(authentication, false);
    if (page < 0 || size < 1 || size > 100) throw SetupException.invalid("Invalid page or size");
    var result = contracts.findByCustodian(custodian.getOrganizationId(), PageRequest.of(page, size));
    return PageResponse.from(result.map(contract -> {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("transactionReference", contract.getTransactionReference());
      item.put("loanRequestId", contract.getLoanRequestId());
      item.put("contractStatus", contract.getContractStatus());
      item.put("quantity", contract.getQuantity());
      item.put("deliveredQuantity", contract.getDeliveredQuantity());
      item.put("returnedQuantity", contract.getReturnedQuantity());
      item.put("outstandingQuantity", contract.getOutstandingQuantity());
      item.put("settlementCurrency", contract.getSettlementCurrency());
      item.put("tradeDate", contract.getTradeDate());
      item.put("settlementDate", contract.getSettlementDate());
      item.put("maturityDate", contract.getMaturityDate());
      item.put("createdAt", contract.getContractCreatedAt());
      return item;
    }));
  }
}
