import { expect, test } from '@playwright/test';
import {
  clearStubRequests,
  expectNotLoggedIn,
  fillCredentials,
  latestEvent,
  startFlow,
  stubRequests,
  submitAndExpectLoggedIn,
  totp,
  userAgentFor,
} from './helpers';

// Post-auth flow: username/password -> minFraud check -> MFA enforcer -> conditional OTP
test.beforeEach(clearStubRequests);

test.describe('low risk', () => {
  test.use({ userAgent: userAgentFor('low') });

  test('allows login and sends the user context to minFraud', async ({ page }) => {
    await startFlow(page, 'post-auth');
    await fillCredentials(page, 'e2e-user');
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));

    const requests = await stubRequests();
    expect(requests).toHaveLength(1);
    const [{ service, accountId, body }] = requests;
    expect(service).toBe('score');
    expect(accountId).toBe('123456');
    expect(body.email.address).toBe('e2e-user@example.com');
    expect(body.event.type).toBe('account_login');
    expect(body.device.user_agent).toContain('e2e-risk/low');
    expect(body.device.ip_address).toBeTruthy();

    const event = await latestEvent('LOGIN', 'post-auth');
    expect(event?.details).toMatchObject({
      maxmind_minfraud_risk_score: expect.stringMatching(/^10(\.0+)?$/),
      maxmind_minfraud_decision: 'ALLOW',
    });
  });
});

test.describe('medium risk', () => {
  test.use({ userAgent: userAgentFor('medium') });

  test('blocks users without MFA configured', async ({ page }) => {
    await startFlow(page, 'post-auth');
    await fillCredentials(page, 'e2e-user');
    await page.locator('#kc-login').click();

    await expect(page.getByText('Multi-factor authentication is required due to suspicious login activity')).toBeVisible();
    await expectNotLoggedIn(page);
  });

  test('lets users with OTP continue to the OTP step', async ({ page }) => {
    await startFlow(page, 'post-auth');
    await fillCredentials(page, 'e2e-otp-user');
    await page.locator('#kc-login').click();

    await page.locator('#otp').fill(totp());
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
  });
});

test.describe('high risk', () => {
  test.use({ userAgent: userAgentFor('high') });

  test('blocks login', async ({ page }) => {
    await startFlow(page, 'post-auth');
    await fillCredentials(page, 'e2e-user');
    await page.locator('#kc-login').click();

    await expect(page.getByText('Login denied due to suspicious activity')).toBeVisible();
    await expectNotLoggedIn(page);
  });
});

test.describe('minFraud API errors', () => {
  test.use({ userAgent: userAgentFor('error') });

  test('FAIL_OPEN allows login', async ({ page }) => {
    await startFlow(page, 'post-auth');
    await fillCredentials(page, 'e2e-user');
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));
    expect(await stubRequests()).toHaveLength(1);
  });

  test('FAIL_CLOSED blocks login', async ({ page }) => {
    await startFlow(page, 'fail-closed');
    await fillCredentials(page, 'e2e-user');
    await page.locator('#kc-login').click();

    await expect(page.getByText('Unable to verify login security')).toBeVisible();
    await expectNotLoggedIn(page);
  });
});

test.describe('IP filtering', () => {
  test.use({ userAgent: userAgentFor('low') });

  test('blocklisted IPs are denied without calling minFraud', async ({ page }) => {
    await startFlow(page, 'ip-blocked');
    await fillCredentials(page, 'e2e-user');
    await page.locator('#kc-login').click();

    await expect(page.getByText('Your IP address has been blocked')).toBeVisible();
    await expectNotLoggedIn(page);
    expect(await stubRequests()).toHaveLength(0);
  });
});

test.describe('device tracking', () => {
  test.use({ userAgent: userAgentFor('low') });

  test('continues without a device ID when device.js cannot load', async ({ page }) => {
    // Keep the test hermetic: the page must fall back to submitting without a device session ID
    await page.route('https://device.maxmind.com/**', (route) => route.abort());
    await startFlow(page, 'device-tracking');
    await fillCredentials(page, 'e2e-user');

    const callback = page.waitForRequest((req) => req.url().startsWith('http://e2e.test/callback'), { timeout: 30_000 });
    await page.locator('#kc-login').click();
    await expect(page.locator('#kc-maxmind-form')).toBeAttached();
    await callback;

    const [{ body }] = await stubRequests();
    expect(body.device.session_id).toBeUndefined();
  });
});
