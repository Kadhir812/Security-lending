package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.HistoryActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString(onlyExplicitlyIncluded = true)
@Table(name = "contract_status_history")
public class ContractStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ToString.Include
  @Column(name = "history_id", nullable = false)
  private Long historyId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  @Column(name = "old_status", length = 40)
  private String oldStatus;

  @Column(name = "new_status", nullable = false, length = 40)
  private String newStatus;

  @Column(name = "changed_by")
  private Long changedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_type", nullable = false)
  private HistoryActorType actorType;

  @Column(name = "reason", nullable = false, length = 500)
  private String reason;

  @Column(name = "source_event_id", length = 36)
  private String sourceEventId;

  @JdbcTypeCode(java.sql.Types.TIMESTAMP)
  @Column(name = "changed_at", nullable = false, columnDefinition = "datetime(6)")
  private Instant changedAt;
}
