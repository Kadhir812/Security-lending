package com.hexaware.borrower_service.kafka.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRoute {
  @NotBlank
  @Size(max = 100)
  private String eventType;

  @NotBlank
  @Size(max = 80)
  private String producerService;

  @NotBlank
  @Size(max = 80)
  private String destinationService;

  @NotBlank
  @Size(max = 255)
  private String topic;
}
