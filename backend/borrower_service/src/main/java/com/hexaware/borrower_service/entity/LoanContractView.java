package com.hexaware.borrower_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "loan_contract_view")
public class LoanContractView {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "contract_view_id", nullable = false)
  private Long contractViewId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "request_reference", nullable = false, unique = true, length = 80)
  private String requestReference;

  @Column(name = "transaction_reference", nullable = false, unique = true, length = 80)
  private String transactionReference;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "contract_snapshot", nullable = false, columnDefinition = "json")
  private Map<String, Object> contractSnapshot;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "received_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant receivedAt;
}
