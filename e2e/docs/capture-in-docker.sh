#!/usr/bin/env sh
# Retakes the docs screenshots in the Playwright image that matches e2e/package.json, so they render
# the same (fonts, anti-aliasing) on every machine and in CI. Needs the stack running (see
# CONTRIBUTING.md); the container shares the host's network to reach it on localhost.
set -eu
cd "$(dirname "$0")/.."
version=$(node -p "require('./package.json').devDependencies['@playwright/test']")
[ -d node_modules ] || npm ci --no-audit --no-fund
# linux/amd64 everywhere: the image CI (ubuntu-latest) uses, also on Apple silicon (emulated)
exec docker run --rm --network host --ipc=host --platform linux/amd64 \
  --env CI --env DOCS_SCREENS --env KEYCLOAK_URL --env MAXMIND_STUB_URL --env KEYCLOAK_PORT --env MAXMIND_STUB_PORT \
  --env KEYCLOAK_ADMIN --env KEYCLOAK_ADMIN_PASSWORD --env HOME=/tmp \
  --user "$(id -u):$(id -g)" \
  --volume "$(cd .. && pwd)":/work --workdir /work/e2e \
  "mcr.microsoft.com/playwright:v${version}-noble" \
  npx playwright test --project docs-screens "$@"
