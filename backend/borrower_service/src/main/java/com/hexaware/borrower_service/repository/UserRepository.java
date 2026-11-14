package com.hexaware.borrower_service.repository;

import com.hexaware.borrower_service.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByIdentityIssuerAndIdentitySubject(String issuer, String subject);

  boolean existsByEmailIgnoreCase(String email);

  Optional<User> findByEmailIgnoreCase(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select user from User user where user.userId = :userId")
  Optional<User> findLockedByUserId(Long userId);
}
