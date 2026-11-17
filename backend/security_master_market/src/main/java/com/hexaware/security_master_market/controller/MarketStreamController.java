package com.hexaware.security_master_market.controller;

import com.hexaware.security_master_market.dto.request.LiveMarketTickDto;
import com.hexaware.security_master_market.service.YahooMarketFeedService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market-stream")
@CrossOrigin(origins = "http://localhost:5173")
public class MarketStreamController {

    private final YahooMarketFeedService feedService;

    public MarketStreamController(YahooMarketFeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping(value = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPrices() {
        return feedService.subscribe();
    }

    @GetMapping("/snapshot")
    public ResponseEntity<Collection<LiveMarketTickDto>> getLatestSnapshot() {
        return ResponseEntity.ok(feedService.getLatestSnapshot());
    }

    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getQuotaStatus() {
        return ResponseEntity.ok(feedService.getQuotaTelemetry());
    }

    @PostMapping("/sync-now")
    public ResponseEntity<List<LiveMarketTickDto>> syncNow() {
        return ResponseEntity.ok(feedService.triggerImmediateBatchSync());
    }
}
