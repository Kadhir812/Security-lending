package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.LocateRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocateRequestRepository extends JpaRepository<LocateRequest, Long> {
  Optional<LocateRequest> findByLocateReference(String locateReference);
}
