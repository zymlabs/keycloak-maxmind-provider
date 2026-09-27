import { createHmac } from 'node:crypto';
import { devices, expect, Locator, Page } from '@playwright/test';
import { adminToken } from '../global-setup';
import { KEYCLOAK_URL, STUB_URL } from '../playwright.config';

export const REALM = 'maxmind-e2e';
export const PASSWORD = 'e2e-password';

// Nothing listens here: tests only assert that Keycloak redirects to it with an authorization code
const REDIRECT_URI = 'http://e2e.test/callback';

export type Risk = 'low' | 'medium' | 'high' | 'error';

/** User-Agent carrying the marker the minFraud stub uses to pick a risk score (see stub/minfraud-stub.mjs). */
export function userAgentFor(risk: Risk) {
  return `${devices['Desktop Chrome'].userAgent} e2e-risk/${risk}`;
}

/** Starts an authorization-code flow for a scenario client (each client is bound to its own browser flow). */
export async function startFlow(page: Page, client: string) {
  const params = new URLSearchParams({
    client_id: `e2e-${client}`,
    redirect_uri: REDIRECT_URI,
    response_type: 'code',
    scope: 'openid',
  });
  await page.goto(`/realms/${REALM}/protocol/openid-connect/auth?${params}`);
}

export async function fillCredentials(page: Page, username: string, password = PASSWORD) {
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
}

/**
 * Submits and waits for Keycloak's redirect back to the client with an authorization code.
 * Watches the request rather than the page: page.route() can't stub navigations that follow a
 * server redirect, so the callback itself fails to load, which is irrelevant here.
 */
export async function submitAndExpectLoggedIn(page: Page, submit: Locator) {
  const callback = page.waitForRequest(
    (req) => req.url().startsWith(REDIRECT_URI) && new URL(req.url()).searchParams.has('code'),
  );
  await submit.click();
  await callback;
}

/** The redirect_uri appears in Keycloak's own URLs, so check where the page is, not what the URL contains. */
export async function expectNotLoggedIn(page: Page) {
  expect(new URL(page.url()).host).not.toBe('e2e.test');
}

export async function clearStubRequests() {
  await fetch(`${STUB_URL}/__requests`, { method: 'DELETE' });
}

/** minFraud requests the stub has received since the last clearStubRequests(). */
export async function stubRequests(): Promise<{ service: string; accountId: string; body: any }[]> {
  return (await fetch(`${STUB_URL}/__requests`)).json();
}

/** Latest admin event of a type for a client, e.g. LOGIN or LOGIN_ERROR. */
export async function latestEvent(type: string, client: string): Promise<any> {
  const token = await adminToken();
  const events = await (
    await fetch(`${KEYCLOAK_URL}/admin/realms/${REALM}/events?type=${type}&client=e2e-${client}&max=1`, {
      headers: { Authorization: `Bearer ${token}` },
    })
  ).json();
  return events[0];
}

/** RFC 6238 TOTP matching the e2e-otp-user credential (key = UTF-8 bytes of the stored secret). */
export function totp(secret = 'e2e-totp-secret-0123456789', period = 30, digits = 6) {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(Math.floor(Date.now() / 1000 / period)));
  const hmac = createHmac('sha1', Buffer.from(secret, 'utf8')).update(counter).digest();
  const offset = hmac[hmac.length - 1] & 0xf;
  const code = (hmac.readUInt32BE(offset) & 0x7fffffff) % 10 ** digits;
  return code.toString().padStart(digits, '0');
}
