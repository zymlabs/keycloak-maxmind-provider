import { createHash } from 'node:crypto';
import { Browser, BrowserContext, expect, Page, test } from '@playwright/test';
import { KEYCLOAK_URL } from '../playwright.config';
import { clearStubRequests, Risk, totp, userAgentFor } from '../tests/helpers';
import { admin, once, openAdminConsole, settled, shot, stableId, writeManifest } from './support';

/**
 * Screenshots for the docs (docs/images, listed in docs/images/manifest.json, shown in
 * docs/screens.md). Run with `npm run docs-screens:docker` against the local stack; never part of the
 * normal e2e run.
 *
 * Everything is shown as a buyer sees it: Keycloak's default themes, in a realm with a neutral
 * demo name, set up through the admin API the way the docs tell an administrator to. minFraud is
 * the e2e stub, which scores each sign-in by a marker in the browser's User-Agent, so no MaxMind
 * account or licence key is involved. Each run deletes and rebuilds the realm, signs in from a
 * fixed documentation address (198.51.100.24, sent as X-Forwarded-For), and pins the few values
 * Keycloak makes up as it goes (event times and ids), so the shots come out the same every time.
 */
// In order, in one worker (the realm is shared), but one failed shot doesn't skip the rest
test.describe.configure({ mode: 'default' });
test.setTimeout(120_000);
test.use({ actionTimeout: 20_000 });

const REALM = 'northwind';
const PASSWORD = 'Northwind-Passw0rd!';
const FLOW = 'browser with minFraud';
const PRE_AUTH_FLOW = 'browser with minFraud pre-auth';
const CLIENT_IP = '198.51.100.24';
const ADMIN = { width: 1440, height: 900 };
const DESKTOP = { width: 1280, height: 800 };
// Not reachable: sign-ins end when Keycloak redirects here with a code, so they record no token events
const PORTAL = 'https://portal.northwind.test/';

const TOTP_SECRET = 'docs-northwind-totp-secret';
const USERS = {
  alex: {
    id: '0b6c1f5e-3c2a-4d8e-9f41-6a7d2b8c9e10', username: 'alex.morgan', email: 'alex.morgan@northwind.test',
    firstName: 'Alex', lastName: 'Morgan',
  },
  sam: {
    id: '5d2e8a41-7b9c-4f3d-8e26-1c4a9b7f0d32', username: 'sam.patel', email: 'sam.patel@northwind.test',
    firstName: 'Sam', lastName: 'Patel',
    otp: {
      type: 'otp', userLabel: 'Phone app', secretData: JSON.stringify({ value: TOTP_SECRET }),
      credentialData: JSON.stringify({ subType: 'totp', digits: 6, period: 30, algorithm: 'HmacSHA1', counter: 0 }),
    },
  },
};

/** The authenticator settings the docs recommend. The stub stands in for minfraud.maxmind.com. */
const MINFRAUD_CONFIG: Record<string, string> = {
  accountId: '123456',
  licenseKey: 'stub-licence-key',
  serviceLevel: 'SCORE',
  deviceTrackingEnabled: 'false',
  recordFraudChecks: 'true',
  ipAllowlist: '10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8,::1/128,fc00::/7,fe80::/10',
  ipBlocklist: '',
  lowRiskThreshold: '30',
  highRiskThreshold: '70',
  lowRiskAction: 'ALLOW',
  mediumRiskAction: 'CHALLENGE',
  highRiskAction: 'BLOCK',
  failMode: 'FAIL_OPEN',
  connectTimeout: '3000',
  readTimeout: '5000',
  apiHost: 'http://maxmind-stub:8081',
};

let browser: Browser;

test.beforeAll(async ({ browser: b }) => {
  browser = b;
  await once('northwind', buildNorthwind);
});

test.afterAll(() => writeManifest());

// ------------------------------------------------------------------ The demo realm

type Execution = { id: string; providerId?: string; displayName: string; level: number; index: number; priority?: number; authenticationFlow?: boolean; requirement: string; authenticationConfig?: string };

const flowPath = (alias: string) => `/${REALM}/authentication/flows/${encodeURIComponent(alias)}`;
const executions = (flow: string) => admin<Execution[]>('GET', `${flowPath(flow)}/executions`);

/** The forms sub-flow of a copy of the browser flow (Keycloak names it after the copy). */
async function formsOf(flow: string): Promise<string> {
  const forms = (await executions(flow)).find((e) => e.authenticationFlow && e.level === 0 && / forms$/i.test(e.displayName));
  if (!forms) throw new Error(`No forms sub-flow in ${flow}`);
  return forms.displayName;
}

/**
 * Adds a REQUIRED step to a (sub-)flow at a priority (lower runs first). The copied browser flow's
 * forms run Username Password Form at 10 and the conditional second factor at 21.
 */
async function addStep(topFlow: string, subFlow: string, provider: string, priority: number, config?: { alias: string; config: Record<string, string> }) {
  await admin('POST', `${flowPath(subFlow)}/executions/execution`, { provider });
  const step = (await executions(topFlow)).find((e) => e.providerId === provider)!;
  await admin('PUT', `${flowPath(topFlow)}/executions`, { ...step, requirement: 'REQUIRED', priority });
  if (config) await admin('POST', `/${REALM}/authentication/executions/${step.id}/config`, config);
}

async function copyBrowserFlow(alias: string, description: string) {
  await admin('POST', `${flowPath('browser')}/copy`, { newName: alias });
  const flow = (await admin<any[]>('GET', `/${REALM}/authentication/flows`)).find((f) => f.alias === alias);
  await admin('PUT', `/${REALM}/authentication/flows/${flow.id}`, { ...flow, description });
}

async function buildNorthwind() {
  await admin('DELETE', `/${REALM}`).catch(() => undefined);
  const user = (u: typeof USERS.alex & { otp?: object }) => ({
    id: u.id, username: u.username, email: u.email, firstName: u.firstName, lastName: u.lastName,
    enabled: true, emailVerified: true,
    credentials: [{ type: 'password', value: PASSWORD, temporary: false }, ...(u.otp ? [u.otp] : [])],
  });
  await admin('POST', '', {
    realm: REALM, displayName: 'Northwind', enabled: true,
    // Sign-ins come "from" a public address (X-Forwarded-For) over plain http on the local stack
    sslRequired: 'none',
    eventsEnabled: true, eventsExpiration: 2592000, enabledEventTypes: ['LOGIN', 'LOGIN_ERROR'],
    users: [user(USERS.alex), user(USERS.sam)],
    clients: [
      { clientId: 'portal', name: 'Northwind portal', publicClient: true, redirectUris: [PORTAL + '*'], standardFlowEnabled: true },
      { clientId: 'partner-portal', name: 'Northwind partner portal', publicClient: true, redirectUris: [PORTAL + '*'], standardFlowEnabled: true },
    ],
  });

  // Post-authentication: a copy of the browser flow with minFraud and the MFA enforcer after the password
  await copyBrowserFlow(FLOW, 'Browser sign-in with a MaxMind minFraud check after the password');
  const forms = await formsOf(FLOW);
  await addStep(FLOW, forms, 'maxmind-minfraud-authenticator', 12, { alias: 'minfraud', config: MINFRAUD_CONFIG });
  await addStep(FLOW, forms, 'maxmind-mfa-enforcer', 14);
  await admin('PUT', `/${REALM}`, { realm: REALM, browserFlow: FLOW });

  // Pre-authentication, for the partner portal: minFraud before the password, then the correlator
  await copyBrowserFlow(PRE_AUTH_FLOW, 'Browser sign-in with a MaxMind minFraud check before the sign-in form');
  const preForms = await formsOf(PRE_AUTH_FLOW);
  await addStep(PRE_AUTH_FLOW, preForms, 'maxmind-minfraud-authenticator', 5, { alias: 'minfraud-pre-auth', config: MINFRAUD_CONFIG });
  await addStep(PRE_AUTH_FLOW, preForms, 'maxmind-preauth-correlator', 12);
  await addStep(PRE_AUTH_FLOW, preForms, 'maxmind-mfa-enforcer', 14);
  const flowId = (await admin<any[]>('GET', `/${REALM}/authentication/flows`)).find((f) => f.alias === PRE_AUTH_FLOW).id;
  const partner = (await admin<any[]>('GET', `/${REALM}/clients?clientId=partner-portal`))[0];
  await admin('PUT', `/${REALM}/clients/${partner.id}`, { ...partner, authenticationFlowBindingOverrides: { browser: flowId } });
}

/** Changes some of the post-authentication minFraud settings; returns a function that puts them back. */
async function withConfig(changes: Record<string, string>): Promise<() => Promise<void>> {
  const step = (await executions(FLOW)).find((e) => e.providerId === 'maxmind-minfraud-authenticator')!;
  const config = await admin('GET', `/${REALM}/authentication/config/${step.authenticationConfig}`);
  await admin('PUT', `/${REALM}/authentication/config/${config.id}`, { ...config, config: { ...config.config, ...changes } });
  return () => admin('PUT', `/${REALM}/authentication/config/${config.id}`, config);
}

// ------------------------------------------------------------------ Browsers

async function adminContext(): Promise<BrowserContext> {
  return browser.newContext({ viewport: ADMIN, deviceScaleFactor: 1, colorScheme: 'light', locale: 'en-US', timezoneId: 'UTC' });
}

/** A user's browser: minFraud scores it `risk`, and Keycloak sees it at the documentation address. */
async function userContext(risk: Risk | 'none'): Promise<BrowserContext> {
  const ctx = await browser.newContext({
    viewport: DESKTOP, deviceScaleFactor: 1, colorScheme: 'light', locale: 'en-US', timezoneId: 'UTC',
    userAgent: risk === 'none' ? undefined : userAgentFor(risk),
    extraHTTPHeaders: { 'X-Forwarded-For': CLIENT_IP },
  });
  // Keep it hermetic: MaxMind's device.js is never loaded
  await ctx.route('https://device.maxmind.com/**', (route) => route.abort());
  return ctx;
}

/** Opens the portal's (or partner portal's) sign-in page. */
async function startSignIn(page: Page, client = 'portal') {
  await page.goto(`${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/auth?` + new URLSearchParams({
    client_id: client, redirect_uri: PORTAL, response_type: 'code', scope: 'openid',
  }));
}

async function enterPassword(page: Page, user: { username: string }) {
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(PASSWORD);
  await page.locator('#kc-login').click();
}

/** Clicks `submit` and waits for Keycloak to send the browser back to the portal with a code. */
async function expectSignedIn(page: Page, submit: () => Promise<unknown>) {
  const back = page.waitForRequest((req) => req.url().startsWith(PORTAL) && new URL(req.url()).searchParams.has('code'));
  await submit();
  await back;
}

/** Opens a flow's page in the admin console. */
async function openFlow(page: Page, flow: string) {
  await page.getByTestId('nav-item-authentication').click();
  await page.getByRole('link', { name: flow, exact: true }).click();
  await expect(page.getByRole('heading', { name: flow })).toBeVisible();
  await settled(page);
}

/** The steps table row of a step. */
const stepRow = (page: Page, step: string) => page.getByRole('row').filter({ has: page.getByTestId(step) });

// ------------------------------------------------------------------ Setup (admin console)

test('setup: flows, steps and settings', async () => {
  const ctx = await adminContext();
  const page = await ctx.newPage();
  await openAdminConsole(page, REALM, 'authentication');
  await settled(page);
  await shot(page, 'setup', 'authentication-flows', {
    section: 'Setup', title: 'Authentication flows',
    caption: 'A copy of the browser flow with minFraud is the realm\'s browser flow; a pre-authentication copy is bound to one client.',
  });

  await page.getByRole('link', { name: FLOW, exact: true }).click();
  await expect(page.getByRole('heading', { name: FLOW })).toBeVisible();
  await settled(page);
  await shot(page, 'setup', 'browser-flow', {
    section: 'Setup', title: 'The browser flow with minFraud',
    caption: 'Post-authentication: minFraud checks the sign-in after the password, and the MFA enforcer acts on a challenge.',
  }, true);

  // Adding the step: the forms sub-flow's "+" menu
  await stepRow(page, `${FLOW} forms`).getByRole('button', { name: 'Add' }).click();
  await page.getByRole('menuitem', { name: /step|execution/i }).first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  // Narrowed to the extension's steps
  await dialog.getByPlaceholder('Search').fill('MaxMind');
  await dialog.getByPlaceholder('Search').press('Enter');
  await expect(dialog.getByText('MaxMind MFA Enforcer', { exact: true })).toBeVisible();
  await dialog.getByText('MaxMind minFraud', { exact: true }).click();
  await shot(page, 'setup', 'add-step', {
    section: 'Setup', title: 'Adding the minFraud step',
    caption: 'The extension adds three steps: MaxMind minFraud, the MFA enforcer and the pre-authentication correlator.',
  });
  await dialog.getByRole('button', { name: 'Cancel' }).click();

  // The step's settings, as an administrator first fills them in (no API host: minfraud.maxmind.com)
  await stepRow(page, 'MaxMind minFraud').getByRole('button', { name: 'Settings' }).click();
  const config = page.getByRole('dialog');
  await expect(config.getByText('MaxMind Account ID')).toBeVisible();
  await config.locator('#apiHost, [id$="apiHost"], [name$="apiHost"]').first().fill('');
  await shot(page, 'setup', 'minfraud-config', {
    section: 'Setup', title: 'minFraud settings',
    caption: 'Account, service level, device tracking, IP lists, risk thresholds and actions, failure mode and timeouts.',
  }, true);
  await config.getByRole('button', { name: 'Cancel' }).click();

  await stepRow(page, 'MaxMind MFA Enforcer').getByRole('button', { name: 'Settings' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('dialog').getByRole('textbox').first().fill('mfa-enforcer');
  await shot(page, 'setup', 'mfa-enforcer-config', {
    section: 'Setup', title: 'MFA enforcer settings',
    caption: 'Which credentials count as a second factor when minFraud asks for one.',
  });
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();

  await openFlow(page, PRE_AUTH_FLOW);
  await shot(page, 'setup', 'pre-auth-flow', {
    section: 'Setup', title: 'The pre-authentication flow',
    caption: 'Pre-authentication: minFraud checks the address before the sign-in form, and the correlator links the check to the user.',
  }, true);
  await ctx.close();
});

// ------------------------------------------------------------------ Sign-in (what users see)

/** Signs `user` in from a browser minFraud scores `risk` and takes a shot of where it ends up. */
async function signInShot(risk: Risk, user: { username: string }, name: string, entry: { title: string; caption: string }, until: (page: Page) => Promise<unknown>) {
  const ctx = await userContext(risk);
  const page = await ctx.newPage();
  await startSignIn(page);
  await enterPassword(page, user);
  await until(page);
  await shot(page, 'sign-in', name, { section: 'Sign-in', ...entry });
  await ctx.close();
}

const error = (text: string) => (page: Page) => expect(page.getByText(text)).toBeVisible();

test('sign-in: device tracking', async () => {
  const restore = await withConfig({ deviceTrackingEnabled: 'true' });
  try {
    const ctx = await userContext('low');
    // The page sends itself on as soon as device.js answers or gives up: hold it so it can be seen
    await ctx.addInitScript(() => { HTMLFormElement.prototype.submit = () => undefined; });
    const page = await ctx.newPage();
    await startSignIn(page);
    await enterPassword(page, USERS.alex);
    await expect(page.locator('#kc-maxmind-form')).toBeVisible();
    await shot(page, 'sign-in', 'device-tracking', {
      section: 'Sign-in', title: 'Device tracking',
      caption: 'With device tracking on, MaxMind\'s device script runs for a moment after the password.',
    });
    await ctx.close();
  } finally {
    await restore();
  }
});

test('sign-in: medium risk asks for the second factor', async () => {
  await signInShot('medium', USERS.sam, 'step-up-otp', {
    title: 'Medium risk: second factor',
    caption: 'A medium score with CHALLENGE: users with a second factor are asked for it.',
  }, (page) => expect(page.locator('#otp')).toBeVisible());
});

test('sign-in: medium risk without a second factor', async () => {
  await signInShot('medium', USERS.alex, 'mfa-required', {
    title: 'Medium risk: no second factor',
    caption: 'The MFA enforcer stops users without one, so they can\'t set one up during a suspicious sign-in.',
  }, error('Multi-factor authentication is required due to suspicious login activity'));
});

test('sign-in: high risk is blocked', async () => {
  await signInShot('high', USERS.alex, 'blocked-high-risk', {
    title: 'High risk: blocked',
    caption: 'A high score with BLOCK denies the sign-in.',
  }, error('Login denied due to suspicious activity'));
});

test('sign-in: blocklisted address', async () => {
  const restore = await withConfig({ ipBlocklist: '198.51.100.0/24' });
  try {
    await signInShot('low', USERS.alex, 'ip-blocked', {
      title: 'Blocklisted address',
      caption: 'Addresses on the IP blocklist are denied without asking minFraud.',
    }, error('Your IP address has been blocked'));
  } finally {
    await restore();
  }
});

test('sign-in: minFraud unavailable with FAIL_CLOSED', async () => {
  const restore = await withConfig({ failMode: 'FAIL_CLOSED' });
  try {
    await signInShot('error', USERS.alex, 'fail-closed', {
      title: 'minFraud unavailable (FAIL_CLOSED)',
      caption: 'When minFraud can\'t be reached, FAIL_CLOSED denies the sign-in (FAIL_OPEN lets it through).',
    }, error('Unable to verify login security'));
  } finally {
    await restore();
  }
});

// ------------------------------------------------------------------ Events (admin console)

/** The first event shown is at 14:02 UTC on 3 March 2026; the rest follow a little apart. */
const EVENTS_START = Date.UTC(2026, 2, 3, 14, 2, 0);

/**
 * Keycloak stamps events with the time and fresh ids (code and session ids, the credential used,
 * minFraud's request id): shows them at fixed times, in the order they happened, and with stable
 * stand-ins for the ids, so the shots don't change from run to run.
 */
async function pinEvents(page: Page) {
  await page.route((url) => url.pathname === `/admin/realms/${REALM}/events`, async (route) => {
    const response = await route.fetch();
    const events: any[] = await response.json();
    const oldestFirst = [...events].sort((a, b) => a.time - b.time);
    for (const event of events) {
      const n = oldestFirst.indexOf(event);
      event.time = EVENTS_START + n * 47_000;
      event.id = stableId(`event-${n}`);
      if (event.sessionId) event.sessionId = stableToken(`session-${event.sessionId}`);
      const details = event.details ?? {};
      if (details.code_id) details.code_id = stableToken(`session-${details.code_id}`);
      if (details.selected_credential_id) details.selected_credential_id = stableId('credential');
      if (details.maxmind_minfraud_request_id) details.maxmind_minfraud_request_id = stableId(`request-${ordinal(details.maxmind_minfraud_request_id)}`);
    }
    await route.fulfill({ response, json: events });
  });
}

/**
 * Real ids differ every run, but the same id must keep the same stand-in (a sign-in's events
 * share a code id): maps each to the order it was first seen in.
 */
const seen = new Map<string, number>();
function ordinal(value: string) {
  if (!seen.has(value)) seen.set(value, seen.size);
  return seen.get(value)!;
}
const stableToken = (value: string) => createHash('sha256').update(`token-${ordinal(value)}`).digest('base64url').slice(0, 24);

test('events: risk details on sign-in events', async () => {
  // A morning's sign-ins: allowed; challenged and passed with a code; challenged without a second factor; blocked
  await admin('DELETE', `/${REALM}/events`);
  await clearStubRequests();
  const signIns = [['low', USERS.alex], ['medium', USERS.sam], ['medium', USERS.alex], ['high', USERS.alex]] as const;
  for (const [risk, user] of signIns) {
    const ctx = await userContext(risk);
    const page = await ctx.newPage();
    await startSignIn(page);
    if (risk === 'low') {
      await expectSignedIn(page, () => enterPassword(page, user));
    } else if (user === USERS.sam) {
      await enterPassword(page, user);
      await page.locator('#otp').fill(totp(TOTP_SECRET));
      await expectSignedIn(page, () => page.locator('#kc-login').click());
    } else {
      await enterPassword(page, user);
      await expect(page.getByText(/suspicious/)).toBeVisible();
    }
    await ctx.close();
  }

  // The extension records some events itself as well as Keycloak's own, so there are more events
  // than sign-ins: wait until they've all been written
  let events: any[] = [];
  await expect.poll(async () => {
    const before = events.length;
    events = await admin<any[]>('GET', `/${REALM}/events`);
    return before > 0 && events.length === before;
  }, { intervals: [500] }).toBe(true);
  // Newest first, as the console lists them
  const allowed = events.findIndex((e) => e.type === 'LOGIN' && e.details?.maxmind_minfraud_decision === 'ALLOW');
  const challenged = events.findIndex((e) => e.details?.maxmind_mfa_enforcer_decision === 'ALLOWED');
  const blocked = events.findIndex((e) => e.error === 'maxmind_high_risk_blocked');

  const ctx = await adminContext();
  const page = await ctx.newPage();
  await pinEvents(page);
  await openAdminConsole(page, REALM, 'events');
  await settled(page);
  const details = page.getByRole('grid', { name: 'Events' }).getByRole('button', { name: 'Details' });
  await expect(details).toHaveCount(Math.min(events.length, 10));
  const toggle = async (i: number) => {
    await details.nth(i).click();
    await settled(page);
  };

  await toggle(allowed);
  await shot(page, 'events', 'login-allowed', {
    section: 'Events', title: 'An allowed sign-in',
    caption: 'A LOGIN event with minFraud\'s risk score, risk level and decision, its request id and the service level.',
  }, true);
  await toggle(allowed);
  await toggle(challenged);
  await shot(page, 'events', 'login-challenged', {
    section: 'Events', title: 'A challenged sign-in',
    caption: 'A medium-risk sign-in (CHALLENGE) that the MFA enforcer let through to the user\'s second factor.',
  }, true);
  await toggle(challenged);
  await toggle(blocked);
  await shot(page, 'events', 'login-blocked', {
    section: 'Events', title: 'A blocked sign-in',
    caption: 'A LOGIN_ERROR event for a high-risk sign-in that minFraud\'s score blocked.',
  }, true);
  await ctx.close();
});
