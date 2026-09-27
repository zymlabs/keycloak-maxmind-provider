import { execFileSync } from 'node:child_process';
import { join } from 'node:path';
import { expect, test } from '@playwright/test';
import {
  clearStubRequests,
  expectNotLoggedIn,
  fillCredentials,
  startFlow,
  stubRequests,
  submitAndExpectLoggedIn,
  userAgentFor,
} from './helpers';

// Pre-auth flow: minFraud check -> username/password -> pre-auth correlator -> MFA enforcer -> conditional OTP
test.beforeEach(clearStubRequests);

/** Runs a query against the compose Postgres (these tests already require the compose stack). */
function sql(query: string): string {
  return execFileSync(
    'docker',
    ['compose', 'exec', '-T', 'postgres', 'psql', '-U', 'keycloak', '-d', 'keycloak', '-tAc', query],
    { cwd: join(__dirname, '..', '..'), encoding: 'utf8' },
  ).trim();
}

test.describe('low risk', () => {
  test.use({ userAgent: userAgentFor('low') });

  test('checks before the login form and correlates the check with the user', async ({ page }) => {
    await startFlow(page, 'pre-auth');

    // The check already ran anonymously by the time the login form is shown
    await expect(page.locator('#username')).toBeVisible();
    const [{ body }] = await stubRequests();
    expect(body.email).toBeUndefined();

    await fillCredentials(page, 'e2e-user');
    await submitAndExpectLoggedIn(page, page.locator('#kc-login'));

    // The correlator attaches the anonymous check to the user after they log in
    const row = sql(
      "select username, is_pre_auth, correlated_at is not null from maxmind_minfraud_check " +
        "where decision = 'ALLOW' order by id desc limit 1",
    );
    expect(row).toBe('e2e-user|f|t');
  });
});

test.describe('high risk', () => {
  test.use({ userAgent: userAgentFor('high') });

  test('blocks before the login form is shown', async ({ page }) => {
    await startFlow(page, 'pre-auth');

    await expect(page.getByText('Login denied due to suspicious activity')).toBeVisible();
    await expect(page.locator('#password')).toHaveCount(0);
    await expectNotLoggedIn(page);
  });
});
