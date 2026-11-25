package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.BorrowerIdentityLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerIdentityLinkRepository
    extends JpaRepository<BorrowerIdentityLink, String> {}
