package com.hexaware.custodian_service.entity;

import com.hexaware.custodian_service.enums.RecallStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recall_request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "recall_id")
  private Long recallId;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "recall_reference", nullable = false, unique = true, length = 80)
  private String recallReference;

  @Column(name = "contract_id", nullable = false)
  private Long contractId;

  @Column(name = "requested_quantity", nullable = false)
  private Long requestedQuantity;

  @Builder.Default
  @Column(name = "returned_quantity", nullable = false)
  private Long returnedQuantity = 0L;

  @Column(name = "requested_by", nullable = false)
  private Long requestedBy;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "due_at", nullable = false)
  private Instant dueAt;

  @Column(name = "recall_reason", nullable = false, length = 500)
  private String recallReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "recall_status", nullable = false)
  private RecallStatus recallStatus;
}
