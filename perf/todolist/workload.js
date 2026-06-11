// Fixed PERFORMANCE workload — identical for every arm.
// Run by the harness as:
//   k6 run -e BASE_URL=... -e WARMUP=30s -e DURATION=120s -e VUS=20 \
//          --summary-export /out/k6-summary.json perf/todolist/workload.js
//
// Mix: 30% add item, 30% check item, 20% read list, 10% read notifications,
//      10% read stats. Latency is tagged per endpoint; thresholds are
//      reporting aids, not gates.

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL;
const VUS = parseInt(__ENV.VUS || '20');

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
    'http_req_duration{endpoint:add_item,phase:load}': ['p(95)<2000'],
    'http_req_duration{endpoint:check_item,phase:load}': ['p(95)<2000'],
    'http_req_duration{endpoint:get_list,phase:load}': ['p(95)<1000'],
    'http_req_duration{endpoint:get_notifications,phase:load}': ['p(95)<1000'],
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
  // a single shared list to read; writes target fresh per-VU lists/items so the
  // perf phase measures latency, not completion contention outcomes
  const lid = uuidv4();
  http.post(`${BASE}/lists`,
    JSON.stringify({ listId: lid, name: 'perf-list' }), JSON_HDRS);
  return { listId: lid };
}

export default function (data) {
  const dice = Math.random();
  if (dice < 0.3) {
    const r = http.post(`${BASE}/lists/${data.listId}/items`, JSON.stringify({
      itemId: uuidv4(), content: 'perf-item',
    }), { ...JSON_HDRS, tags: { endpoint: 'add_item' } });
    check(r, { 'item accepted': (res) => res.status === 200 || res.status === 202 });
  } else if (dice < 0.6) {
    // add-then-check a fresh item so the check always targets a real id
    const iid = uuidv4();
    http.post(`${BASE}/lists/${data.listId}/items`, JSON.stringify({
      itemId: iid, content: 'perf-item',
    }), JSON_HDRS);
    const r = http.put(`${BASE}/lists/${data.listId}/items/${iid}/check`,
      null, { tags: { endpoint: 'check_item' } });
    check(r, { 'check accepted': (res) => res.status === 200 || res.status === 202 });
  } else if (dice < 0.8) {
    const r = http.get(`${BASE}/lists/${data.listId}`,
      { tags: { endpoint: 'get_list' } });
    check(r, { 'list readable': (res) => res.status === 200 || res.status === 404 });
  } else if (dice < 0.9) {
    const r = http.get(`${BASE}/lists/${data.listId}/notifications`,
      { tags: { endpoint: 'get_notifications' } });
    check(r, { 'notifications readable': (res) => res.status === 200 || res.status === 404 });
  } else {
    const r = http.get(`${BASE}/stats/lists`, { tags: { endpoint: 'get_stats' } });
    check(r, { 'stats readable': (res) => res.status === 200 });
  }
  sleep(0.1);
}
