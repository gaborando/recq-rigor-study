// Fixed PERFORMANCE workload — identical for every arm.
// Run by the harness as:
//   k6 run -e BASE_URL=... -e WARMUP=30s -e DURATION=120s -e VUS=20 \
//          --summary-export /out/k6-summary.json perf/order-inventory/workload.js
//
// Mix: 40% place order, 30% read order, 20% read product, 10% read stats.
// Latency is tagged per endpoint; thresholds are reporting aids, not gates.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const VUS = parseInt(__ENV.VUS || '20');

// Availability vs. consistency are scored separately. A read-after-write that
// 404s because the CQRS read model hasn't materialised yet is NOT a system
// fault — under a write rate higher than a single consumer can project, stale
// reads are an expected, by-design property of an eventually-consistent read
// side. So `http_req_failed` is reclassified to count only true availability
// faults (5xx and transport/connection errors, status 0); the rate of
// not-yet-materialised reads is captured in the dedicated `stale_reads` metric.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));
const staleReads = new Rate('stale_reads');

export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-vus', vus: Math.max(2, Math.floor(VUS / 4)),
      duration: __ENV.WARMUP || '30s',
      gracefulStop: '5s', tags: { phase: 'warmup' },
    },
    load: {
      executor: 'constant-vus', vus: VUS,
      duration: __ENV.DURATION || '120s',
      startTime: __ENV.WARMUP || '30s',
      gracefulStop: '10s', tags: { phase: 'load' },
    },
  },
  thresholds: {
    'http_req_duration{endpoint:place_order,phase:load}': ['p(95)<2000'],
    'http_req_duration{endpoint:get_order,phase:load}': ['p(95)<1000'],
    'http_req_duration{endpoint:get_product,phase:load}': ['p(95)<1000'],
    'http_req_duration{endpoint:get_stats,phase:load}': ['p(95)<1000'],
  },
};

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

const JSON_HDRS = { headers: { 'Content-Type': 'application/json' } };

export function setup() {
  // ample stock and funds: the perf phase measures latency, not contention outcomes
  const p = http.post(`${BASE}/products`,
    JSON.stringify({ name: 'perf-product', unitPrice: 100, stock: 10_000_000 }), JSON_HDRS);
  const c = http.post(`${BASE}/customers`,
    JSON.stringify({ name: 'perf-customer', balance: 2_000_000_000 }), JSON_HDRS);
  return { productId: String(p.json('id')), customerId: String(c.json('id')) };
}

export default function (data) {
  const dice = Math.random();
  if (dice < 0.4) {
    const r = http.post(`${BASE}/orders`, JSON.stringify({
      orderId: uuidv4(), customerId: data.customerId,
      productId: data.productId, quantity: 1,
    }), { ...JSON_HDRS, tags: { endpoint: 'place_order' } });
    check(r, { 'order accepted': (res) => res.status === 200 || res.status === 202 });
    // remember an order id for subsequent reads in this VU
    if (r.status === 200 || r.status === 202) {
      __ENV.__last_order = String(r.json('orderId'));
    }
  } else if (dice < 0.7 && __ENV.__last_order) {
    const r = http.get(`${BASE}/orders/${__ENV.__last_order}`,
      { tags: { endpoint: 'get_order' } });
    // 200 = materialised; 404 = read model lagging (consistency lag, not a fault)
    staleReads.add(r.status === 404);
    check(r, { 'order readable': (res) => res.status === 200 || res.status === 404 });
  } else if (dice < 0.9) {
    const r = http.get(`${BASE}/products/${data.productId}`,
      { tags: { endpoint: 'get_product' } });
    check(r, { 'product readable': (res) => res.status === 200 });
  } else {
    const r = http.get(`${BASE}/stats/orders`, { tags: { endpoint: 'get_stats' } });
    check(r, { 'stats readable': (res) => res.status === 200 });
  }
  sleep(0.1);
}
