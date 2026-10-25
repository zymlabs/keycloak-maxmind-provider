import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { expect, Locator, Page } from '@playwright/test';
import { adminToken } from '../global-setup';
import { KEYCLOAK_URL } from '../playwright.config';

/** Helpers for the documentation screenshots (`npm run docs-screens`). */

const REPO = join(__dirname, '..', '..');
const IMAGES = join(REPO, 'docs', 'images');

export type Section = 'Setup' | 'Sign-in' | 'Events';
const SECTIONS: Section[] = ['Setup', 'Sign-in', 'Events'];

interface Entry {
  section: Section;
  title: string;
  file: string;
  width: number;
  height: number;
  caption: string;
}

/**
 * Playwright starts a new worker after a failed test, which reloads this module: the run's shots
 * and "the realm is built" are kept in files named after the test runner's process, so a failed
 * shot neither loses the others from the manifest nor rebuilds the realm halfway through.
 */
const RUN = join(__dirname, '..', 'test-results', `docs-screens-${process.ppid}`);
mkdirSync(RUN, { recursive: true });
const ENTRIES = join(RUN, 'entries.json');
const entries: Entry[] = existsSync(ENTRIES) ? JSON.parse(readFileSync(ENTRIES, 'utf8')) : [];

/** Runs `build` once per test run (see above). */
export async function once(name: string, build: () => Promise<void>) {
  const marker = join(RUN, `${name}.done`);
  if (existsSync(marker)) return;
  await build();
  writeFileSync(marker, '');
}

/**
 * Saves a screenshot under docs/images/<folder>/<name>.png and records it for the manifest.
 * `fit` first makes the window tall enough for panels and dialogs that scroll to show everything.
 */
export async function shot(page: Page, folder: string, name: string, entry: Omit<Entry, 'file' | 'width' | 'height'>, fit = false) {
  const path = join(IMAGES, folder, `${name}.png`);
  mkdirSync(dirname(path), { recursive: true });
  // Let fonts, icons and transitions settle so shots are the same from run to run
  await page.evaluate(() => document.fonts.ready);
  // No hover highlight or tooltip wherever the last click left the mouse, and no focus ring
  await page.mouse.move(0, 0);
  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.waitForTimeout(300);
  const restore = fit ? await fitViewport(page) : async () => undefined;
  await page.screenshot({ path, animations: 'disabled', caret: 'hide' });
  await restore();

  const png = readFileSync(path);
  const file = relative(REPO, path).split('\\').join('/');
  const record: Entry = { ...entry, file, width: png.readUInt32BE(16), height: png.readUInt32BE(20) };
  const at = entries.findIndex((e) => e.file === file);
  if (at >= 0) entries[at] = record;
  else entries.push(record);
  writeFileSync(ENTRIES, JSON.stringify(entries));
}

/**
 * The admin console scrolls inside its panels and dialogs, which a screenshot doesn't capture:
 * makes the window tall enough for each of them to show all of its content, and returns a
 * function that puts the window back.
 */
async function fitViewport(page: Page): Promise<() => Promise<void>> {
  const original = page.viewportSize()!;
  for (let i = 0; i < 4; i++) {
    const extra = await page.evaluate(() => Math.max(0, ...[...document.querySelectorAll('body *')].map((e) => {
      // The side menu may stay clipped: only the page's content counts
      if (e.closest('nav, .pf-v5-c-page__sidebar')) return 0;
      const overflow = getComputedStyle(e).overflowY;
      return (overflow === 'auto' || overflow === 'scroll') && e.scrollHeight > e.clientHeight + 1 ? e.scrollHeight - e.clientHeight : 0;
    })));
    if (!extra) break;
    const now = page.viewportSize()!;
    await page.setViewportSize({ width: now.width, height: Math.min(now.height + extra, 4000) });
    await page.waitForTimeout(200);
  }
  return () => page.setViewportSize(original);
}

/**
 * Writes docs/images/manifest.json (every screenshot, by section, in the order taken) and the
 * gallery, docs/screens.md. A run of only some tests replaces the shots it retook and keeps the rest.
 */
export function writeManifest() {
  mkdirSync(IMAGES, { recursive: true });
  const path = join(IMAGES, 'manifest.json');
  const kept: Entry[] = existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : [];
  const retaken = new Map(entries.map((e) => [e.file, e]));
  const merged = [
    ...kept.map((e) => retaken.get(e.file) ?? e),
    ...entries.filter((e) => !kept.some((k) => k.file === e.file)),
  ].filter((e) => existsSync(join(REPO, e.file)));
  const ordered = SECTIONS.flatMap((s) => merged.filter((e) => e.section === s));
  writeFileSync(path, JSON.stringify(ordered, null, 2) + '\n');
  writeFileSync(join(REPO, 'docs', 'screens.md'), gallery(ordered));
}

const anchor = (heading: string) => heading.toLowerCase().replace(/[^a-z0-9 -]/g, '').replace(/ /g, '-');

/** docs/screens.md: every screenshot, by section, two to a row. */
function gallery(manifest: Entry[]): string {
  const image = (e: Entry) => {
    const src = e.file.replace(/^docs\//, '');
    return `<td valign="top" width="400"><a href="${src}"><img src="${src}" width="400" alt="${e.caption.replace(/"/g, '&quot;')}"></a><br><b>${e.title}</b><br>${e.caption}</td>`;
  };
  const sections = SECTIONS.filter((s) => manifest.some((e) => e.section === s));
  const lines = [
    '# Screens',
    '',
    'What the extension looks like in Keycloak\'s default theme: setting it up in the admin console,',
    'what users see when a sign-in is checked, challenged or blocked, and the events it records.',
    'Select an image for the full size.',
    '',
    'Generated by `npm run docs-screens:docker` in `e2e/` (see [CONTRIBUTING.md](../CONTRIBUTING.md#docs-screenshots)):',
    'don\'t edit by hand.',
    '',
    ...sections.map((s) => `- [${s}](#${anchor(s)})`),
    '',
  ];
  for (const section of sections) {
    const shots = manifest.filter((e) => e.section === section);
    const rows: string[] = [];
    for (let i = 0; i < shots.length; i += 2) rows.push(`<tr>${shots.slice(i, i + 2).map(image).join('')}</tr>`);
    lines.push(`## ${section}`, '', '<table>', ...rows, '</table>', '');
  }
  return lines.join('\n');
}

/** Admin REST calls (master admin), signing in again when the short-lived token runs out. */
let token: string | undefined;
export async function admin<T = any>(method: string, path: string, body?: unknown): Promise<T> {
  token ??= await adminToken();
  const send = () => fetch(`${KEYCLOAK_URL}/admin/realms${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let res = await send();
  if (res.status === 401) {
    token = await adminToken();
    res = await send();
  }
  if (res.status >= 400) throw new Error(`${method} ${path}: ${res.status} ${await res.text()}`);
  const text = await res.text();
  return (text.startsWith('{') || text.startsWith('[') ? JSON.parse(text) : text) as T;
}

/** Signs in to Keycloak's admin console as the master administrator and opens a page of a realm. */
export async function openAdminConsole(page: Page, realm: string, path: string) {
  await page.goto(`${KEYCLOAK_URL}/admin/master/console/#/${realm}/${path}`);
  await page.locator('#username').fill(process.env.KEYCLOAK_ADMIN ?? 'admin');
  await page.locator('#password').fill(process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin');
  await page.locator('#kc-login').click();
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
  // The stack's bootstrap admin gets a "temporary admin" warning that a real installation doesn't show
  await page.addStyleTag({ content: '.pf-v5-c-banner { display: none !important; }' });
}

/** Waits until the admin console has loaded its data (no spinners). */
export async function settled(page: Page) {
  await page.waitForLoadState('networkidle');
  await expect(page.locator('.pf-v5-c-spinner, .pf-v6-c-spinner')).toHaveCount(0, { timeout: 15_000 });
}

/** A stable stand-in for a random value, e.g. an id, so it reads the same in every run. */
export function stableId(seed: string): string {
  const hex = createHash('sha256').update(seed).digest('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-a${hex.slice(17, 20)}-${hex.slice(20, 32)}`;
}
