/**
 * Minimal stand-in for the MaxMind minFraud web service, for e2e tests.
 *
 * The risk score is chosen by a marker in the device User-Agent, so tests control it per browser
 * context in both pre-auth (no email) and post-auth modes:
 *   "e2e-risk/low" -> 10, "e2e-risk/medium" -> 50, "e2e-risk/high" -> 95,
 *   "e2e-risk/error" -> HTTP 500, anything else -> 1
 *
 * GET /__requests returns every recorded minFraud request; DELETE /__requests clears them.
 */
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';

const PORT = Number(process.env.PORT ?? 8081);
const SCORES = { low: 10, medium: 50, high: 95 };
const requests = [];

function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

createServer((req, res) => {
  let raw = '';
  req.on('data', (chunk) => (raw += chunk));
  req.on('end', () => {
    if (req.url === '/__requests') {
      if (req.method === 'DELETE') requests.length = 0;
      return send(res, 200, requests);
    }

    const match = req.method === 'POST' && req.url?.match(/^\/minfraud\/v2\.0\/(score|insights|factors)$/);
    if (!match) return send(res, 404, { code: 'NOT_FOUND', error: `No stub for ${req.method} ${req.url}` });

    const auth = Buffer.from((req.headers.authorization ?? '').replace(/^Basic /, ''), 'base64').toString();
    const body = raw ? JSON.parse(raw) : {};
    requests.push({ service: match[1], accountId: auth.split(':')[0], body, receivedAt: new Date().toISOString() });

    const marker = /e2e-risk\/(\w+)/.exec(body.device?.user_agent ?? '')?.[1];
    if (marker === 'error') {
      return send(res, 500, { code: 'SERVER_ERROR', error: 'Stubbed minFraud failure' });
    }

    const riskScore = SCORES[marker] ?? 1;
    send(res, 200, {
      id: randomUUID(),
      risk_score: riskScore,
      funds_remaining: 100.0,
      queries_remaining: 10000,
      ip_address: { risk: riskScore },
      disposition: { action: 'accept', reason: 'default', rule_label: null },
      warnings: [],
    });
  });
}).listen(PORT, () => console.log(`minFraud stub listening on ${PORT}`));
