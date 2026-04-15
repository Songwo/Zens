const http = require('http');
const https = require('https');
const { performance } = require('perf_hooks');

const target = process.argv[2] || 'http://127.0.0.1:7800/section/active';
const concurrency = Number(process.argv[3] || 200);
const totalRequests = Number(process.argv[4] || 5000);
const timeoutMs = Number(process.argv[5] || 10000);

const url = new URL(target);
const client = url.protocol === 'https:' ? https : http;
const agent = url.protocol === 'https:'
  ? new https.Agent({ keepAlive: true, maxSockets: concurrency, maxFreeSockets: concurrency })
  : new http.Agent({ keepAlive: true, maxSockets: concurrency, maxFreeSockets: concurrency });

let started = 0;
let completed = 0;
let inFlight = 0;
let success = 0;
let failed = 0;
const latencies = [];
const statusCounts = new Map();
const startedAt = performance.now();

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[index];
}

function recordStatus(statusCode) {
  const key = String(statusCode);
  statusCounts.set(key, (statusCounts.get(key) || 0) + 1);
}

function completeOne(ok, latencyMs, statusCode) {
  latencies.push(latencyMs);
  if (ok) {
    success += 1;
  } else {
    failed += 1;
  }
  if (statusCode) {
    recordStatus(statusCode);
  }
  completed += 1;
  inFlight -= 1;

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
  inFlight += 1;
  const requestStartedAt = performance.now();

  const req = client.request({
    protocol: url.protocol,
    hostname: url.hostname,
    port: url.port,
    path: `${url.pathname}${url.search}`,
    method: 'GET',
    agent,
    timeout: timeoutMs,
    headers: {
      Connection: 'keep-alive',
    },
  }, (res) => {
    res.on('data', () => {});
    res.on('end', () => {
      const latencyMs = performance.now() - requestStartedAt;
      const ok = res.statusCode >= 200 && res.statusCode < 400;
      completeOne(ok, latencyMs, res.statusCode);
    });
  });

  req.on('timeout', () => {
    req.destroy(new Error('timeout'));
  });

  req.on('error', () => {
    const latencyMs = performance.now() - requestStartedAt;
    completeOne(false, latencyMs, 'ERR');
  });

  req.end();
}

function finish() {
  agent.destroy();
  const durationMs = performance.now() - startedAt;
  latencies.sort((a, b) => a - b);
  const requestsPerSecond = totalRequests / (durationMs / 1000);

  const statusSummary = Array.from(statusCounts.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([code, count]) => `${code}:${count}`)
    .join(', ');

  const summary = {
    target,
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
    statusSummary,
  };

  console.log(JSON.stringify(summary, null, 2));
}

const initial = Math.min(concurrency, totalRequests);
for (let i = 0; i < initial; i += 1) {
  runOne();
}
