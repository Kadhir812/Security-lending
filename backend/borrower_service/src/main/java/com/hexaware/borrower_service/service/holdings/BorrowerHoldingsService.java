package com.hexaware.borrower_service.service.holdings;

import com.hexaware.borrower_service.dto.response.BorrowerHoldingPosition;
import com.hexaware.borrower_service.dto.response.BorrowerHoldingsResponse;
import com.hexaware.borrower_service.entity.LoanRequest;
import com.hexaware.borrower_service.enums.BorrowerTradeType;
import com.hexaware.borrower_service.enums.LoanRequestStatus;
import com.hexaware.borrower_service.repository.BorrowerTradeActivityRepository;
import com.hexaware.borrower_service.repository.LoanContractViewRepository;
import com.hexaware.borrower_service.repository.LoanRequestRepository;
import com.hexaware.borrower_service.service.loan.LoanRequestSubmissionValidator;
import com.hexaware.borrower_service.client.SecurityReferenceClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BorrowerHoldingsService {
  private final LoanRequestSubmissionValidator authorization;
  private final LoanRequestRepository requests;
  private final LoanContractViewRepository contracts;
  private final BorrowerTradeActivityRepository trades;
  private final SecurityReferenceClient securities;

  @Transactional(readOnly = true)
  public BorrowerHoldingsResponse holdings(Authentication authentication) {
    var borrower = authorization.requireBorrower(authentication);
    var loanRequests = requests.findByBorrowerOrganizationReferenceOrderByRequestedAtDesc(
        borrower.getOrganizationReference());
    Set<String> requestReferences = new HashSet<>();
    loanRequests.stream().filter(request -> request.getStatus() == LoanRequestStatus.APPROVED)
        .forEach(request -> requestReferences.add(request.getRequestReference()));
    var snapshots = contracts.findByRequestReferenceIn(requestReferences);
    Map<String, LoanRequest> requestsByReference = new HashMap<>();
    loanRequests.forEach(request -> requestsByReference.put(request.getRequestReference(), request));
    var tradeRows = trades.findByBorrowerOrganizationReferenceOrderByTradeIdAsc(
        borrower.getOrganizationReference());
    Map<String, List<com.hexaware.borrower_service.entity.BorrowerTradeActivity>> tradesByContract = new HashMap<>();
    tradeRows.stream().filter(trade -> trade.getTransactionReference() != null)
        .forEach(trade -> tradesByContract.computeIfAbsent(trade.getTransactionReference(), ignored -> new ArrayList<>()).add(trade));

    Map<String, Long> borrowed = new HashMap<>();
    Map<String, Long> shortOpen = new HashMap<>();
    long borrowedContractCount = 0;
    for (var view : snapshots) {
      var request = requestsByReference.get(view.getRequestReference());
      if (request == null || request.getStatus() != LoanRequestStatus.APPROVED) continue;
      Map<String, Object> snapshot = view.getContractSnapshot();
      long outstanding = number(snapshot.get("outstandingQuantity"));
      if (outstanding <= 0) continue;
      String isin = request.getSecurityIsin();
      borrowed.merge(isin, outstanding, Math::addExact);
      borrowedContractCount++;
      long openShortForContract = 0;
      for (var trade : tradesByContract.getOrDefault(view.getTransactionReference(), List.of())) {
        if (trade.getTradeType() == BorrowerTradeType.SELL_SHORT) openShortForContract += trade.getQuantity();
        else if (trade.getTradeType() == BorrowerTradeType.BUY_TO_COVER) openShortForContract -= trade.getQuantity();
      }
      shortOpen.merge(isin, Math.max(0, openShortForContract), Math::addExact);
    }

    Map<String, Long> owned = new HashMap<>();
    for (var trade : tradeRows) {
      if (trade.getSecurityIsin() == null) continue;
      if (trade.getTradeType() == BorrowerTradeType.BUY) owned.merge(trade.getSecurityIsin(), trade.getQuantity(), Math::addExact);
      else if (trade.getTradeType() == BorrowerTradeType.SELL) owned.merge(trade.getSecurityIsin(), -trade.getQuantity(), Math::addExact);
    }

    Set<String> isins = new HashSet<>();
    isins.addAll(borrowed.keySet()); isins.addAll(owned.keySet());
    isins.addAll(shortOpen.keySet());
    List<BorrowerHoldingPosition> positions = new ArrayList<>();
    for (String isin : isins) {
      var security = securities.require(isin);
      long borrowedQuantity = borrowed.getOrDefault(isin, 0L);
      long shortQuantity = shortOpen.getOrDefault(isin, 0L);
      positions.add(new BorrowerHoldingPosition(
          isin, security.symbol(), security.securityName(), security.securityType(),
          security.currency(), Math.max(0, owned.getOrDefault(isin, 0L)),
          borrowedQuantity, shortQuantity, Math.max(0, borrowedQuantity - shortQuantity)));
    }
    positions.sort((left, right) -> left.securityIsin().compareTo(right.securityIsin()));
    return new BorrowerHoldingsResponse(positions.size(), borrowedContractCount, List.copyOf(positions));
  }

  private long number(Object value) {
    if (value instanceof Number number) return number.longValue();
    if (value == null) return 0;
    try { return Long.parseLong(value.toString()); }
    catch (NumberFormatException exception) { return 0; }
  }
}
