import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { KEYCLOAK_URL, STUB_URL } from './playwright.config';
import { REALM } from './tests/helpers';

/**
 * (Re)creates the e2e realm through the admin API so every run starts from a clean state,
 * then binds each client to its scenario's browser flow. Flow binding overrides need flow ids,
 * which are only known after import.
 */
export default async function globalSetup() {
  await waitFor(`${KEYCLOAK_URL}/realms/master`, 'Keycloak');
  await waitFor(`${STUB_URL}/__requests`, 'minFraud stub (docker compose --profile e2e up)');

  const token = await adminToken();
  const api = (path: string, init: RequestInit = {}) =>
    fetch(`${KEYCLOAK_URL}/admin/realms${path}`, {
      ...init,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...init.headers },
    });

  const serverInfo = await (await fetch(`${KEYCLOAK_URL}/admin/serverinfo`, {
    headers: { Authorization: `Bearer ${token}` },
  })).json();
  console.log(`Keycloak ${serverInfo.systemInfo.version}`);

  await api(`/${REALM}`, { method: 'DELETE' });
  const realm = readFileSync(join(__dirname, 'realms', 'maxmind-e2e-realm.json'), 'utf8');
  await expectOk(await api('', { method: 'POST', body: realm }), 'import realm');

  const flows: { id: string; alias: string }[] = await (await api(`/${REALM}/authentication/flows`)).json();
  const clients: any[] = await (await api(`/${REALM}/clients`)).json();

  for (const client of clients.filter((c) => c.clientId.startsWith('e2e-'))) {
    const scenario = client.clientId.slice('e2e-'.length);
    const flow = flows.find((f) => f.alias === `e2e-browser-${scenario}`);
    if (!flow) throw new Error(`No browser flow for client ${client.clientId}`);
    client.authenticationFlowBindingOverrides = { browser: flow.id };
    await expectOk(
      await api(`/${REALM}/clients/${client.id}`, { method: 'PUT', body: JSON.stringify(client) }),
      `bind flow for ${client.clientId}`,
    );
  }
}

async function waitFor(url: string, what: string, timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      if ((await fetch(url)).ok) return;
    } catch {
      // not up yet
    }
    await new Promise((r) => setTimeout(r, 2_000));
  }
  throw new Error(`${what} not reachable at ${url}`);
}

export async function adminToken(): Promise<string> {
  const res = await fetch(`${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    body: new URLSearchParams({
      client_id: 'admin-cli',
      grant_type: 'password',
      username: process.env.KEYCLOAK_ADMIN ?? 'admin',
      password: process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin',
    }),
  });
  await expectOk(res, 'admin login');
  return (await res.json()).access_token;
}

async function expectOk(res: Response, what: string) {
  if (!res.ok) throw new Error(`${what} failed: ${res.status} ${await res.text()}`);
}
