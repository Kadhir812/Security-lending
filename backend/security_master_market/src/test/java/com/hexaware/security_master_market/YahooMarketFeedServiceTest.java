package com.hexaware.security_master_market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hexaware.security_master_market.repository.SecurityMasterRepository;
import com.hexaware.security_master_market.service.SecurityPriceService;
import com.hexaware.security_master_market.service.YahooMarketFeedService;
import org.junit.jupiter.api.Test;

class YahooMarketFeedServiceTest {
  @Test
  void disabledFeedDoesNotReadSecuritiesOrFetchPrices() {
    SecurityMasterRepository securities = mock(SecurityMasterRepository.class);
    SecurityPriceService prices = mock(SecurityPriceService.class);
    YahooMarketFeedService feed = new YahooMarketFeedService(securities, prices, false);

    feed.pollBatchQuotes();
    assertThat(feed.triggerImmediateBatchSync()).isEmpty();
    verifyNoInteractions(securities, prices);
  }
}
