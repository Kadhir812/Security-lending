const API_BASE = (import.meta.env.VITE_SECURITY_MASTER_URL || '/api/v1').replace(/\/$/, '');

async function request(path, options) {
  const response = await fetch(`${API_BASE}${path}`, options);
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `Security Master request failed (HTTP ${response.status})`);
  }
  if (response.status === 204) return null;
  return response.json();
}

export async function fetchAllSecurities() {
  const data = await request('/securities?page=0&size=100');
  return Array.isArray(data) ? data : data.content || [];
}

export function fetchSecurityByIsin(isin) {
  return request(`/securities/${encodeURIComponent(isin)}`);
}

export function createSecurityMaster(security) {
  return request('/securities', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      symbol: security.symbol,
      name: security.name,
      isin: security.isin,
      exchange: security.exchange,
      currency: security.currency,
      type: security.type,
      status: security.status
    })
  });
}

export function updateSecurityStatus(id, status, version) {
  return request(`/securities/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status, version })
  });
}

export function fetchSecurityPrices(id, params = {}) {
  const query = new URLSearchParams(params);
  return request(`/securities/${encodeURIComponent(id)}/prices${query.size ? `?${query}` : ''}`);
}

export function submitManualPrice(id, price) {
  return request(`/securities/${encodeURIComponent(id)}/prices`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      marketPrice: price,
      priceTimestamp: new Date().toISOString(),
      sourceName: 'MANUAL_OVERRIDE'
    })
  });
}

export function fetchMarketSnapshot() {
  return request('/market-stream/snapshot');
}

export function fetchMarketQuota() {
  return request('/market-stream/quota');
}

export function triggerManualBatchSync() {
  return request('/market-stream/sync-now', { method: 'POST' });
}

export function subscribeMarketStream(onBatchUpdate, onStatusChange) {
  const eventSource = new EventSource(`${API_BASE}/market-stream/prices`);
  eventSource.onopen = () => onStatusChange('LIVE_SSE');
  eventSource.addEventListener('price-batch', (event) => {
    try {
      onBatchUpdate(JSON.parse(event.data));
    } catch (error) {
      console.error('Invalid SSE payload', error);
    }
  });
  eventSource.onerror = () => {
    onStatusChange('SIMULATED_STREAM');
    eventSource.close();
  };
  return () => eventSource.close();
}
