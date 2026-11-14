package com.hexaware.security_master_market.repository;

import com.hexaware.security_master_market.entity.SecurityPrice;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface SecurityPriceRepository
    extends JpaRepository<SecurityPrice, Long>, JpaSpecificationExecutor<SecurityPrice> {
  boolean existsBySecurityId(Long securityId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<SecurityPrice> findBySecurityIdAndPriceTimestampAndSourceName(
      Long securityId, Instant timestamp, String source);

  Optional<SecurityPrice>
      findFirstBySecurityIdAndSourceNameAndPriceTimestampLessThanEqualOrderByPriceTimestampDesc(
          Long securityId, String source, Instant at);
}
