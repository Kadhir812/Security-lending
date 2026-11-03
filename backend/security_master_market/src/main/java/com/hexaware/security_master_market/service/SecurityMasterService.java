package com.hexaware.security_master_market.service;

import com.hexaware.security_master_market.dto.request.CreateSecurityRequest;
import com.hexaware.security_master_market.dto.request.UpdateSecurityRequest;
import com.hexaware.security_master_market.dto.request.UpdateSecurityStatusRequest;
import com.hexaware.security_master_market.dto.response.PageResponse;
import com.hexaware.security_master_market.dto.response.SecurityResponse;
import com.hexaware.security_master_market.entity.SecurityMaster;
import com.hexaware.security_master_market.enums.SecurityStatus;
import com.hexaware.security_master_market.enums.SecurityType;
import com.hexaware.security_master_market.exception.MarketDataException;
import com.hexaware.security_master_market.mapper.SecurityMapper;
import com.hexaware.security_master_market.repository.SecurityMasterRepository;
import com.hexaware.security_master_market.repository.SecurityPriceRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SecurityMasterService {
  private final SecurityMasterRepository securities;
  private final SecurityPriceRepository prices;
  private final SecurityMapper mapper;

  @Transactional
  public SecurityResponse register(CreateSecurityRequest request) {
    SecurityMaster security =
        SecurityMaster.builder()
            .isin(MarketInput.isin(request.getIsin()))
            .symbol(MarketInput.upper(request.getSymbol(), "symbol", 50))
            .securityName(MarketInput.text(request.getSecurityName(), "securityName", 255))
            .securityType(request.getSecurityType())
            .currency(MarketInput.currency(request.getCurrency()))
            .exchangeName(MarketInput.optional(request.getExchangeName(), "exchange", 100))
            .status(request.getStatus())
            .build();
    return mapper.security(securities.saveAndFlush(security));
  }

  @Transactional(readOnly = true)
  public SecurityResponse details(String isin) {
    return mapper.security(require(isin));
  }

  public SecurityMaster require(String isin) {
    return securities
        .findByIsin(MarketInput.isin(isin))
        .orElseThrow(() -> MarketDataException.missing("Unknown security"));
  }

  public SecurityMaster lock(String isin) {
    return securities
        .findLockedByIsin(MarketInput.isin(isin))
        .orElseThrow(() -> MarketDataException.missing("Unknown security"));
  }

  @Transactional
  public SecurityResponse update(String isin, UpdateSecurityRequest request) {
    SecurityMaster security = lock(isin);
    checkVersion(security, request.getVersion());
    String currency = MarketInput.currency(request.getCurrency());
    if (!currency.equals(security.getCurrency())
        && prices.existsBySecurityId(security.getSecurityId()))
      throw MarketDataException.conflict("Currency cannot change after prices have been recorded");
    security.setSymbol(MarketInput.upper(request.getSymbol(), "symbol", 50));
    security.setSecurityName(MarketInput.text(request.getSecurityName(), "securityName", 255));
    security.setSecurityType(request.getSecurityType());
    security.setCurrency(currency);
    security.setExchangeName(MarketInput.optional(request.getExchangeName(), "exchange", 100));
    security.setStatus(request.getStatus());
    securities.flush();
    return mapper.security(security);
  }

  @Transactional
  public SecurityResponse status(String isin, UpdateSecurityStatusRequest request) {
    SecurityMaster security = lock(isin);
    checkVersion(security, request.getVersion());
    security.setStatus(request.getStatus());
    securities.flush();
    return mapper.security(security);
  }

  private void checkVersion(SecurityMaster security, Long version) {
    if (!Objects.equals(security.getVersion(), version))
      throw MarketDataException.conflict("Outdated security version; reload before updating");
  }

  @Transactional(readOnly = true)
  public PageResponse<SecurityResponse> search(
      String query,
      SecurityType type,
      SecurityStatus status,
      String currency,
      String exchange,
      boolean activeOnly,
      int page,
      int size) {
    String term = MarketInput.optional(query, "q", 255);
    String selectedCurrency = currency == null ? null : MarketInput.currency(currency);
    String selectedExchange = MarketInput.optional(exchange, "exchange", 100);
    if (activeOnly && status == SecurityStatus.INACTIVE)
      throw MarketDataException.invalid("activeOnly conflicts with INACTIVE status");
    Specification<SecurityMaster> specification =
        (root, criteria, cb) -> {
          var filters = new ArrayList<Predicate>();
          if (term != null) {
            String pattern =
                "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            filters.add(
                cb.or(
                    cb.like(root.get("symbol"), pattern, '!'),
                    cb.like(root.get("securityName"), pattern, '!'),
                    cb.like(root.get("isin"), pattern, '!')));
          }
          if (type != null) filters.add(cb.equal(root.get("securityType"), type));
          if (status != null || activeOnly)
            filters.add(cb.equal(root.get("status"), activeOnly ? SecurityStatus.ACTIVE : status));
          if (selectedCurrency != null)
            filters.add(cb.equal(root.get("currency"), selectedCurrency));
          if (selectedExchange != null)
            filters.add(cb.equal(root.get("exchangeName"), selectedExchange));
          return cb.and(filters.toArray(Predicate[]::new));
        };
    return PageResponse.from(
        securities
            .findAll(specification, MarketInput.page(page, size, Sort.by("symbol", "isin")))
            .map(mapper::security));
  }
}
