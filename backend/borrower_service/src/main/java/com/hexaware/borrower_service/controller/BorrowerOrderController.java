package com.hexaware.borrower_service.controller;

import com.hexaware.borrower_service.dto.request.BorrowerOrderSubmission;
import com.hexaware.borrower_service.dto.response.BorrowerOrderResponse;
import com.hexaware.borrower_service.dto.response.BorrowerTradeActivityResponse;
import com.hexaware.borrower_service.service.orders.BorrowerTradeActivityService;
import com.hexaware.borrower_service.service.orders.BorrowerOrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class BorrowerOrderController {
  private final BorrowerOrderService orders;
  private final BorrowerTradeActivityService trades;

  @PostMapping
  public ResponseEntity<BorrowerOrderResponse> submit(
      Authentication authentication,
      @RequestHeader("Idempotency-Key") String orderReference,
      @Valid @RequestBody BorrowerOrderSubmission submission) {
    BorrowerOrderResponse response = orders.submit(authentication, orderReference, submission);
    return ResponseEntity.created(URI.create("/api/orders/" + response.orderReference()))
        .body(response);
  }

  @GetMapping
  public List<BorrowerOrderResponse> list(Authentication authentication) {
    return orders.list(authentication);
  }

  @GetMapping("/{orderReference}")
  public BorrowerOrderResponse get(
      Authentication authentication, @PathVariable String orderReference) {
    return orders.get(authentication, orderReference);
  }

  @PostMapping("/{orderReference}/cancel")
  public BorrowerOrderResponse cancel(
      Authentication authentication, @PathVariable String orderReference) {
    return orders.cancel(authentication, orderReference);
  }

  @GetMapping("/{orderReference}/trades")
  public List<BorrowerTradeActivityResponse> trades(
      Authentication authentication, @PathVariable String orderReference) {
    return trades.listForOrder(authentication, orderReference);
  }
}
