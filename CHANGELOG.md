# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security
- **IP allowlist/blocklist and the minFraud request no longer resolve hostnames through DNS.** `InetAddress.getByName` was called on list entries and on the client address, so a hostname entry was silently resolved on every login. That let whoever controls (or spoofs) the DNS record decide who is allowlisted, and allowlisted clients skip the minFraud check entirely. It also put blocking DNS lookups on the login path. Only IPv4/IPv6 literals are now accepted: hostname entries never match and are skipped with a warning in the log, while the other entries in the list still apply. A client address that isn't an IP literal is treated as a minFraud API error (handled by the configured fail mode). Bracketed IPv6 entries such as `[::1]` are no longer accepted; write them without brackets.

### Added
- **CI upgrade check**: imports the example realms on Keycloak 24.0.0 and upgrades the same database to 26.7.4
- **API Host** setting (`apiHost`) to target MaxMind's sandbox (`sandbox.maxmind.com`) or a local stub; empty keeps `minfraud.maxmind.com`
- **Playwright end-to-end tests** (`e2e/`) against a minFraud stub, covering risk levels, MFA enforcement, fail modes, IP blocklist, device tracking fallback and pre-auth correlation
- **CI compatibility matrix**: unit tests compiled against Keycloak 24.0.0, 25.0.6 and 26.7.4, and e2e tests run the released JAR on each version; releases now require both to pass

### Changed
- Build against Keycloak 26.7.4 (was 24.0.0); Keycloak 24.0.0 remains the minimum supported version
- Upgraded the MaxMind minFraud SDK from 1.16 to 4.4. It uses the JDK HTTP client, so Apache httpclient is no longer bundled. Its Jackson is bundled and relocated so it can't conflict with Keycloak's (this replaces the unrelocated, vulnerable jackson-databind 2.15.3). minFraud clients are reused per configuration instead of being created for every login
- `raw_response` is stored using the SDK's own JSON serialization, and timeout errors read `request timed out` / `HTTP connect timed out`
- Docker Compose uses the official `quay.io/keycloak/keycloak` image (was `bitnamilegacy/keycloak`), selectable with `KEYCLOAK_VERSION`, and waits for Postgres to be healthy
- Migrated GitVersion from 5.x to 6.8 (`gittools/actions@v4`). Release versions are unchanged; develop pre-release numbers now increase by one per commit, and feature branch versions include the branch name
- Updated test dependencies, Maven plugins and GitHub Actions

### Fixed
- Example realm (`examples/realms/maxmind-demo-realm.json`) now includes Keycloak's built-in `browser`, `direct grant`, `registration` and `saml ecp` flows. A realm imported from the old file on Keycloak 24.0.0 or 24.0.1 has no `browser` flow, and Keycloak 26.6.1+ then fails to start when upgrading it (`MigrateTo26_6_1` NullPointerException). Those realms need a `browser` flow added before upgrading; see [Troubleshooting](docs/TROUBLESHOOTING.md#keycloak-fails-to-start-after-upgrading-to-2661-or-later)
- Blocklisted users saw the raw message key `ipAddressBlocked` instead of an error message
