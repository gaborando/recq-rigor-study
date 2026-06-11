// Fixed PERFORMANCE workload — identical for every arm.
// Run by the harness as:
//   k6 run -e BASE_URL=... -e WARMUP=30s -e DURATION=120s -e VUS=20 \
//          --summary-export /out/k6-summary.json perf/flight/workload.js
//
// Mix: 40% book seat, 30% read booking, 20% read flight, 10% read stats.
// Latency is tagged per endpoint; thresholds are reporting aids, not gates.
//
// The setup flight is provisioned with a huge seat count so the perf phase
// measures latency, not seat contention (each VU books a distinct seat index).

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL;
const VUS = parseInt(__ENV.VUS || '20');
const SEAT_COUNT = 200000;

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
    'http_req_duration{endpoint:book_seat,phase:load}': ['p(95)<2000'],
    'http_req_duration{endpoint:get_booking,phase:load}': ['p(95)<1000'],
    'http_req_duration{endpoint:get_flight,phase:load}': ['p(95)<1000'],
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

// Generate a seat id from an index using the "<row><letter>" convention
// (6 letters per row). Matches a flight provisioned with SEAT_COUNT seats.
const LETTERS = ['A', 'B', 'C', 'D', 'E', 'F'];
function seatFor(idx) {
  const i = idx % SEAT_COUNT;
  const row = Math.floor(i / LETTERS.length) + 1;
  return `${row}${LETTERS[i % LETTERS.length]}`;
}

export function setup() {
  const f = http.post(`${BASE}/flights`,
    JSON.stringify({ seatCount: SEAT_COUNT, seatPrice: 100 }), JSON_HDRS);
  const c = http.post(`${BASE}/customers`,
    JSON.stringify({ name: 'perf-customer', balance: 2_000_000_000 }), JSON_HDRS);
  return { flightId: String(f.json('id')), customerId: String(c.json('id')) };
}

export default function (data) {
  const dice = Math.random();
  if (dice < 0.4) {
    // each VU+iteration targets a distinct seat to avoid contention noise
    const idx = (__VU * 100000 + __ITER) % SEAT_COUNT;
    const r = http.post(`${BASE}/bookings`, JSON.stringify({
      bookingId: uuidv4(), customerId: data.customerId,
      flightId: data.flightId, seat: seatFor(idx),
    }), { ...JSON_HDRS, tags: { endpoint: 'book_seat' } });
    check(r, { 'booking accepted': (res) => res.status === 200 || res.status === 202 });
    if (r.status === 200 || r.status === 202) {
      __ENV.__last_booking = String(r.json('bookingId'));
    }
  } else if (dice < 0.7 && __ENV.__last_booking) {
    const r = http.get(`${BASE}/bookings/${__ENV.__last_booking}`,
      { tags: { endpoint: 'get_booking' } });
    check(r, { 'booking readable': (res) => res.status === 200 || res.status === 404 });
  } else if (dice < 0.9) {
    const r = http.get(`${BASE}/flights/${data.flightId}`,
      { tags: { endpoint: 'get_flight' } });
    check(r, { 'flight readable': (res) => res.status === 200 });
  } else {
    const r = http.get(`${BASE}/stats/bookings`, { tags: { endpoint: 'get_stats' } });
    check(r, { 'stats readable': (res) => res.status === 200 });
  }
  sleep(0.1);
}
