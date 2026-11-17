package com.hexaware.security_master_market.service;

import com.hexaware.security_master_market.dto.request.LiveMarketTickDto;
import com.hexaware.security_master_market.dto.request.SubmitPriceRequest;
import com.hexaware.security_master_market.entity.SecurityMaster;
import com.hexaware.security_master_market.repository.SecurityMasterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class YahooMarketFeedService {

    private static final Logger log = LoggerFactory.getLogger(YahooMarketFeedService.class);
    private static final int HARD_HOURLY_CAP = 58;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<String> TRACKED_SYMBOLS = Set.of("INFY", "TCS");

    private final SecurityMasterRepository securityMasterRepository;
    private final SecurityPriceService securityPriceService;
    private final RestClient restClient;
    private final boolean feedEnabled;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<String, LiveMarketTickDto> latestSnapshotCache = new ConcurrentHashMap<>();
    private final AtomicInteger hourlyRequestCount = new AtomicInteger(0);
    private volatile long currentHourBucket = Instant.now().getEpochSecond() / 3600;
    private volatile Instant lastSyncTime = Instant.now();

    public YahooMarketFeedService(SecurityMasterRepository securityMasterRepository,
                                  SecurityPriceService securityPriceService,
                                  @Value("${app.market.feed.enabled:false}") boolean feedEnabled) {
        this.securityMasterRepository = securityMasterRepository;
        this.securityPriceService = securityPriceService;
        this.feedEnabled = feedEnabled;
        this.restClient = RestClient.builder()
                .baseUrl("https://query1.finance.yahoo.com")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        if (!latestSnapshotCache.isEmpty()) {
            try {
                emitter.send(SseEmitter.event()
                        .name("price-batch")
                        .data(new ArrayList<>(latestSnapshotCache.values())));
            } catch (Exception e) {
                log.warn("Removing SSE subscriber that rejected the initial snapshot", e);
                emitters.remove(emitter);
            }
        }
        return emitter;
    }

    public Collection<LiveMarketTickDto> getLatestSnapshot() {
        return latestSnapshotCache.values();
    }

    public Map<String, Object> getQuotaTelemetry() {
        resetBucketIfNewHour();
        return Map.of(
                "usedThisHour", hourlyRequestCount.get(),
                "limitPerHour", 60,
                "remaining", Math.max(0, 60 - hourlyRequestCount.get()),
                "activeSubscribers", emitters.size(),
                "marketOpenIst", isIndianMarketOpen(),
                "lastSyncTime", lastSyncTime.toString()
        );
    }

    public List<LiveMarketTickDto> triggerImmediateBatchSync() {
        pollBatchQuotes();
        return new ArrayList<>(latestSnapshotCache.values());
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 2_000)
    public synchronized void pollBatchQuotes() {
        if (!feedEnabled) {
            return;
        }

        resetBucketIfNewHour();

        List<SecurityMaster> activeSecurities = securityMasterRepository.findAll().stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(String.valueOf(s.getStatus())))
                .filter(s -> TRACKED_SYMBOLS.contains(s.getSymbol()))
                .toList();

        if (activeSecurities.isEmpty()) {
            return;
        }

        if (hourlyRequestCount.get() >= HARD_HOURLY_CAP) {
            log.warn("Yahoo Finance hourly quota guard reached ({}/60). Serving cached snapshot.", hourlyRequestCount.get());
            broadcast(new ArrayList<>(latestSnapshotCache.values()));
            return;
        }

        Map<String, SecurityMaster> tickerMap = activeSecurities.stream()
                .collect(Collectors.toMap(
                        this::toYahooTicker,
                        s -> s,
                        (first, second) -> first
                ));

        String symbolsParam = String.join(",", tickerMap.keySet());

        try {
            int count = hourlyRequestCount.incrementAndGet();
            log.info("Executing Yahoo batch quote request #{}/60 for {} securities", count, tickerMap.size());

            Map<?, ?> response = restClient.get()
                    .uri("/v7/finance/quote?symbols={symbols}", symbolsParam)
                    .retrieve()
                    .body(Map.class);

            List<LiveMarketTickDto> batch = parseAndStore(response, tickerMap, count);
            if (!batch.isEmpty()) {
                lastSyncTime = Instant.now();
                broadcast(batch);
            }
        } catch (Exception ex) {
            log.warn("Yahoo Finance upstream unreachable ({}). Broadcasting cached prices.", ex.getMessage());
            broadcast(new ArrayList<>(latestSnapshotCache.values()));
        }
    }

    @SuppressWarnings("unchecked")
    private List<LiveMarketTickDto> parseAndStore(Map<?, ?> response,
                                                  Map<String, SecurityMaster> tickerMap,
                                                  int quotaUsed) {
        List<LiveMarketTickDto> updates = new ArrayList<>();
        if (response == null || !response.containsKey("quoteResponse")) {
            return updates;
        }

        Map<String, Object> quoteResponse = (Map<String, Object>) response.get("quoteResponse");
        List<Map<String, Object>> results = (List<Map<String, Object>>) quoteResponse.get("result");
        if (results == null) {
            return updates;
        }

        for (Map<String, Object> q : results) {
            String ySymbol = String.valueOf(q.get("symbol"));
            SecurityMaster sec = tickerMap.get(ySymbol);
            if (sec == null || q.get("regularMarketPrice") == null) {
                continue;
            }

            BigDecimal price = toBigDecimal(q.get("regularMarketPrice"));
            BigDecimal prevClose = toBigDecimal(q.getOrDefault("regularMarketPreviousClose", price));
            BigDecimal openPrice = toBigDecimal(q.getOrDefault("regularMarketOpen", prevClose));
            BigDecimal change = toBigDecimal(q.getOrDefault("regularMarketChange", price.subtract(prevClose)));
            BigDecimal changePct = toBigDecimal(q.getOrDefault("regularMarketChangePercent", BigDecimal.ZERO));
            BigDecimal dayHigh = toBigDecimal(q.getOrDefault("regularMarketDayHigh", price));
            BigDecimal dayLow = toBigDecimal(q.getOrDefault("regularMarketDayLow", price));
            BigDecimal bid = toBigDecimal(q.getOrDefault("bid", price.multiply(BigDecimal.valueOf(0.9995)))).setScale(2, RoundingMode.HALF_UP);
            BigDecimal ask = toBigDecimal(q.getOrDefault("ask", price.multiply(BigDecimal.valueOf(1.0005)))).setScale(2, RoundingMode.HALF_UP);
            Long volume = q.get("regularMarketVolume") instanceof Number n ? n.longValue() : 0L;

            persistPriceSafely(sec, price);

            String secId = String.valueOf(sec.getSecurityId());
            LiveMarketTickDto dto = new LiveMarketTickDto(
                    secId,
                    sec.getSymbol(),
                    sec.getSecurityName(),
                    sec.getIsin(),
                    sec.getExchangeName() != null ? sec.getExchangeName() : "NSE",
                    sec.getCurrency() != null ? sec.getCurrency() : "INR",
                    price,
                    prevClose,
                    openPrice,
                    change,
                    changePct,
                    dayHigh,
                    dayLow,
                    bid,
                    ask,
                    volume,
                    Instant.now(),
                    "YAHOO_BATCH_1M",
                    quotaUsed,
                    60
            );

            latestSnapshotCache.put(secId, dto);
            updates.add(dto);
        }
        return updates;
    }

    private void persistPriceSafely(SecurityMaster sec, BigDecimal price) {
        try {
            SubmitPriceRequest req = new SubmitPriceRequest();
            req.setMarketPrice(price);
            req.setPriceTimestamp(Instant.now().truncatedTo(ChronoUnit.MICROS));
            req.setSourceName("YAHOO_BATCH_1M");
            securityPriceService.submit(sec.getIsin(), req);
        } catch (Exception e) {
            log.warn("Unable to persist Yahoo price for ISIN {}", sec.getIsin(), e);
        }
    }

    private void broadcast(List<LiveMarketTickDto> updates) {
        if (updates.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("price-batch")
                        .data(updates));
            } catch (Exception e) {
                log.debug("Removing failed SSE subscriber", e);
                emitters.remove(emitter);
            }
        }
    }

    private void resetBucketIfNewHour() {
        long nowBucket = Instant.now().getEpochSecond() / 3600;
        if (nowBucket != currentHourBucket) {
            currentHourBucket = nowBucket;
            hourlyRequestCount.set(0);
        }
    }

    private boolean isIndianMarketOpen() {
        ZonedDateTime nowIst = ZonedDateTime.now(IST);
        DayOfWeek day = nowIst.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = nowIst.toLocalTime();
        return !time.isBefore(LocalTime.of(9, 15)) && !time.isAfter(LocalTime.of(15, 30));
    }

    private String toYahooTicker(SecurityMaster security) {
        String symbol = security.getSymbol();
        return symbol.contains(".")
                ? symbol
                : symbol + ("BSE".equalsIgnoreCase(security.getExchangeName()) ? ".BO" : ".NS");
    }

    private BigDecimal toBigDecimal(Object val) {
        return new BigDecimal(String.valueOf(val)).setScale(2, RoundingMode.HALF_UP);
    }
}
