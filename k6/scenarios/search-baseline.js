import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import { BASE_URL } from '../lib/config.js';

// ENV
//   MODE: normal | hotkey   (default: normal)
//   RATE: requests per second (default: 200)
//   DURATION: 30s (default)
//   START_DATE: 2026-05-01 (시드 시작일과 일치해야 함)
//   YEAR_DAYS: 365
const MODE = __ENV.MODE || 'normal';
const RATE = parseInt(__ENV.RATE || '200');
const DURATION = __ENV.DURATION || '30s';
const START_DATE = __ENV.START_DATE || '2026-05-01';
const YEAR_DAYS = parseInt(__ENV.YEAR_DAYS || '365');
const HOT_RATIO = parseFloat(__ENV.HOT_RATIO || '0.7');
const MANAGEMENT_URL = __ENV.MANAGEMENT_URL || 'http://localhost:9090';

// === 메트릭 ===
const durationNormal = new Trend('duration_normal', true);
const durationHot = new Trend('duration_hot', true);
const countNormal = new Counter('count_normal');
const countHot = new Counter('count_hot');
const countError = new Counter('count_error');

// === 데이터 분포 ===
const REGIONS_ALL = ['제주', '강릉', '부산', '서울', '경주', '여수', '속초', '기타'];
const REGIONS_HOT = ['제주', '강릉'];
const SORT_OPTIONS = ['popular', 'price_asc', 'price_desc', 'rating'];

// 주말 시작일 (5/1~12/31 사이 토요일)
const WEEKEND_STARTS = buildWeekendStarts(START_DATE, YEAR_DAYS);

function buildWeekendStarts(startDateStr, yearDays) {
  const out = [];
  const start = new Date(startDateStr);
  for (let i = 0; i < yearDays - 3; i++) {
    const d = new Date(start);
    d.setDate(d.getDate() + i);
    if (d.getDay() === 6) { // 토요일
      out.push(formatDate(d));
    }
  }
  return out;
}

function formatDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function addDays(dateStr, days) {
  const d = new Date(dateStr);
  d.setDate(d.getDate() + days);
  return formatDate(d);
}

function pickRequest() {
  const useHot = (MODE === 'hotkey') && (Math.random() < HOT_RATIO);

  if (useHot) {
    // Hot Key: 인기 지역 + 주말 + 2박 + 인원 2 + popular 정렬
    const region = REGIONS_HOT[Math.floor(Math.random() * REGIONS_HOT.length)];
    const checkIn = WEEKEND_STARTS[Math.floor(Math.random() * WEEKEND_STARTS.length)];
    const checkOut = addDays(checkIn, 2);
    return { region, check_in: checkIn, check_out: checkOut, guests: 2, sort: 'popular', tag: 'hot' };
  }

  // 정상: 지역×날짜×인원×정렬 균등
  const region = REGIONS_ALL[Math.floor(Math.random() * REGIONS_ALL.length)];
  const startOffset = Math.floor(Math.random() * (YEAR_DAYS - 5));
  const checkIn = addDays(START_DATE, startOffset);
  const nights = 1 + Math.floor(Math.random() * 5);
  const checkOut = addDays(checkIn, nights);
  const guests = 1 + Math.floor(Math.random() * 4);
  const sort = SORT_OPTIONS[Math.floor(Math.random() * SORT_OPTIONS.length)];
  return { region, check_in: checkIn, check_out: checkOut, guests, sort, tag: 'normal' };
}

function buildUrl(req) {
  const params = new URLSearchParams({
    region: req.region,
    check_in: req.check_in,
    check_out: req.check_out,
    guests: req.guests,
    sort: req.sort,
    page: '1',
    size: '20',
  }).toString();
  return `${BASE_URL}/api/v1/search/hotels?${params}`;
}

export const options = {
  scenarios: {
    search: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 100,
      maxVUs: 5000,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)', 'count'],
  thresholds: {
    'count_error': ['count<10'],
  },
};

export function setup() {
  console.log(`=== Search Baseline | MODE=${MODE} | RATE=${RATE} | DURATION=${DURATION} ===`);
  const res = http.get(`${MANAGEMENT_URL}/actuator/health`);
  if (res.status !== 200) {
    throw new Error(`서버 비활성: ${res.status}`);
  }
}

export default function () {
  const req = pickRequest();
  const url = buildUrl(req);
  const res = http.get(url, { tags: { type: req.tag } });

  const ok = check(res, {
    'status 200': (r) => r.status === 200,
  });

  if (!ok) {
    countError.add(1);
    return;
  }

  if (req.tag === 'hot') {
    durationHot.add(res.timings.duration);
    countHot.add(1);
  } else {
    durationNormal.add(res.timings.duration);
    countNormal.add(1);
  }
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`summary-${MODE}.json`]: JSON.stringify(data),
  };
}
