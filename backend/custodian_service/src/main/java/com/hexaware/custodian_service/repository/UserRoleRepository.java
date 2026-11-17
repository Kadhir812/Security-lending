package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
  @EntityGraph(attributePaths = {"role"})
  List<UserRole> findByUserUserId(Long userId);
}
