package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
  Optional<Role> findByRoleName(String roleName);
}
