package com.hexaware.custodian_service.repository;

import com.hexaware.custodian_service.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  @EntityGraph(attributePaths = {"organization"})
  Optional<User> findByIdentityIssuerAndIdentitySubject(String issuer, String subject);

  boolean existsByEmailIgnoreCase(String email);

  @EntityGraph(attributePaths = {"organization"})
  Optional<User> findByEmailIgnoreCase(String email);
}
