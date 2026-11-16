package com.hexaware.custodian_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "margin_call_transaction_log")
public class MarginCallTransactionLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "log_id")
  private Long logId;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "margin_call_id", nullable = false)
  private Long marginCallId;

  @Column(name = "settlement_instruction_id")
  private Long settlementInstructionId;

  @Column(name = "activity_type", nullable = false, length = 30)
  private String activityType;

  @Column(precision = 20, scale = 2)
  private BigDecimal amount;

  @Column(name = "source_event_id", length = 36)
  private String sourceEventId;

  @Column(length = 500)
  private String notes;

  @Column(name = "activity_timestamp", nullable = false)
  private Instant activityTimestamp;
}
