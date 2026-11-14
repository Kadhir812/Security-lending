package com.hexaware.security_master_market.repository;

import com.hexaware.security_master_market.entity.SecurityMaster;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SecurityMasterRepository
    extends JpaRepository<SecurityMaster, Long>, JpaSpecificationExecutor<SecurityMaster> {
  Optional<SecurityMaster> findByIsin(String isin);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SecurityMaster s where s.isin = :isin")
  Optional<SecurityMaster> findLockedByIsin(String isin);
}
