const http = require('http');
const https = require('https');
const { performance } = require('perf_hooks');

const baseUrl = process.argv[2] || 'http://127.0.0.1:7800';
const concurrency = Number(process.argv[3] || 200);
const totalRequests = Number(process.argv[4] || 5000);
const timeoutMs = Number(process.argv[5] || 10000);

const authToken = process.env.LOADTEST_AUTH_TOKEN || '';
const postIds = (process.env.LOADTEST_POST_IDS || '')
  .split(',')
  .map((item) => item.trim())
  .filter(Boolean);

const listBody = process.env.LOADTEST_LIST_BODY
  ? JSON.parse(process.env.LOADTEST_LIST_BODY)
  : { page: 1, pageSize: 15, status: 1, orderBy: 'new' };

const scenarios = [
  {
    name: 'home-bootstrap',
    weight: 25,
    build: () => ({
      method: 'GET',
      path: '/public/home-bootstrap?hotTagLimit=10&hotRankLimit=5&timeRange=WEEK',
    }),
  },
  {
    name: 'post-search',
    weight: 40,
    build: () => ({
      method: 'POST',
      path: '/post/search-lists',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(listBody),
    }),
  },
  {
    name: 'view-log',
    weight: 25,
    enabled: () => postIds.length > 0,
    build: () => ({
      method: 'POST',
      path: `/view-log/record?postId=${encodeURIComponent(randomPick(postIds))}&device=loadtest`,
    }),
  },
  {
    name: 'post-like',
    weight: 10,
    enabled: () => postIds.length > 0 && !!authToken,
    build: () => ({
      method: 'POST',
      path: `/post/${encodeURIComponent(randomPick(postIds))}/like`,
      headers: { Authorization: `Bearer ${authToken}` },
    }),
  },
];

const enabledScenarios = scenarios.filter((scenario) => !scenario.enabled || scenario.enabled());
if (enabledScenarios.length === 0) {
  throw new Error('No enabled scenarios. Set LOADTEST_POST_IDS or LOADTEST_AUTH_TOKEN if needed.');
}

const rootUrl = new URL(baseUrl);
const client = rootUrl.protocol === 'https:' ? https : http;
const agent = rootUrl.protocol === 'https:'
  ? new https.Agent({ keepAlive: true, maxSockets: concurrency, maxFreeSockets: concurrency })
  : new http.Agent({ keepAlive: true, maxSockets: concurrency, maxFreeSockets: concurrency });

let started = 0;
let completed = 0;
let success = 0;
let failed = 0;
const latencies = [];
const startedAt = performance.now();
const statusCounts = new Map();
const scenarioStats = new Map();

function randomPick(items) {
  return items[Math.floor(Math.random() * items.length)];
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[index];
}

function chooseScenario() {
  const totalWeight = enabledScenarios.reduce((sum, item) => sum + item.weight, 0);
  let cursor = Math.random() * totalWeight;
  for (const scenario of enabledScenarios) {
    cursor -= scenario.weight;
    if (cursor <= 0) return scenario;
  }
  return enabledScenarios[enabledScenarios.length - 1];
}

function recordStatus(statusCode) {
  const key = String(statusCode);
  statusCounts.set(key, (statusCounts.get(key) || 0) + 1);
}

function recordScenario(name, ok, latencyMs) {
  const current = scenarioStats.get(name) || { count: 0, success: 0, failed: 0, totalLatency: 0 };
  current.count += 1;
  current.totalLatency += latencyMs;
  if (ok) {
    current.success += 1;
  } else {
    current.failed += 1;
  }
  scenarioStats.set(name, current);
}

function completeOne(scenarioName, ok, latencyMs, statusCode) {
  latencies.push(latencyMs);
  recordScenario(scenarioName, ok, latencyMs);
  if (ok) {
    success += 1;
  } else {
    failed += 1;
  }
  if (statusCode) {
    recordStatus(statusCode);
  }
  completed += 1;

  if (started < totalRequests) {
    runOne();
    return;
  }

  if (completed === totalRequests) {
    finish();
  }
}

function runOne() {
  started += 1;
  const scenario = chooseScenario();
  const request = scenario.build();
  const requestStartedAt = performance.now();

  const req = client.request({
    protocol: rootUrl.protocol,
    hostname: rootUrl.hostname,
    port: rootUrl.port,
    path: request.path,
    method: request.method,
    agent,
    timeout: timeoutMs,
    headers: {
      Connection: 'keep-alive',
      ...(request.headers || {}),
    },
  }, (res) => {
    res.on('data', () => {});
    res.on('end', () => {
      const latencyMs = performance.now() - requestStartedAt;
      const ok = res.statusCode >= 200 && res.statusCode < 400;
      completeOne(scenario.name, ok, latencyMs, res.statusCode);
    });
  });

  req.on('timeout', () => {
    req.destroy(new Error('timeout'));
  });

  req.on('error', () => {
    const latencyMs = performance.now() - requestStartedAt;
    completeOne(scenario.name, false, latencyMs, 'ERR');
  });

  if (request.body) {
    req.write(request.body);
  }
  req.end();
}

function finish() {
  agent.destroy();
  const durationMs = performance.now() - startedAt;
  latencies.sort((a, b) => a - b);
  const requestsPerSecond = totalRequests / (durationMs / 1000);

  const scenarioSummary = Array.from(scenarioStats.entries()).map(([name, stats]) => ({
    name,
    count: stats.count,
    success: stats.success,
    failed: stats.failed,
    avgLatencyMs: Number((stats.totalLatency / Math.max(stats.count, 1)).toFixed(2)),
  }));

  const summary = {
    baseUrl,
    concurrency,
    totalRequests,
    success,
    failed,
    durationMs: Number(durationMs.toFixed(2)),
    requestsPerSecond: Number(requestsPerSecond.toFixed(2)),
    latencyMs: {
      min: Number((latencies[0] || 0).toFixed(2)),
      avg: Number((latencies.reduce((sum, val) => sum + val, 0) / Math.max(latencies.length, 1)).toFixed(2)),
      p50: Number(percentile(latencies, 50).toFixed(2)),
      p95: Number(percentile(latencies, 95).toFixed(2)),
      p99: Number(percentile(latencies, 99).toFixed(2)),
      max: Number((latencies[latencies.length - 1] || 0).toFixed(2)),
    },
    statusSummary: Array.from(statusCounts.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([code, count]) => `${code}:${count}`)
      .join(', '),
    scenarioSummary,
  };

  console.log(JSON.stringify(summary, null, 2));
}

const initial = Math.min(concurrency, totalRequests);
for (let i = 0; i < initial; i += 1) {
  runOne();
}
