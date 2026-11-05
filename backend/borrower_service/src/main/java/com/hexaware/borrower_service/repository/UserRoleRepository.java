package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.UserRole;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
  @EntityGraph(attributePaths = {"role"})
  List<UserRole> findByUserUserId(Long userId);
}
