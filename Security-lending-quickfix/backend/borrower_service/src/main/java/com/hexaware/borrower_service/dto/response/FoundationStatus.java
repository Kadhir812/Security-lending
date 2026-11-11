package com.hexaware.borrower_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FoundationStatus {
  private String service;
  private int stage;
}
