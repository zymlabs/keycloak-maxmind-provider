# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Keycloak authentication extension that integrates MaxMind minFraud fraud detection into the browser login flow. The extension intercepts authentication, calls the MaxMind API to assess login risk, and takes configurable actions (allow, challenge with MFA, or block) based on risk scores.

**Minimum Keycloak Version**: 24.0.0+ (fixes known Admin UI bug from version 22.x)

## Build Commands

```bash
# Build the extension
mvn clean package

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=RiskEvaluationTest

# Run specific test method
mvn test -Dtest=RiskEvaluationTest#testLowRiskScore

# Run tests with verbose output
mvn test -X

# Build without tests
mvn clean package -DskipTests

# Install to local Maven repo
mvn clean install
```

Output JAR: `target/zymlabs-maxmind-provider.jar`

## Testing

### Test Coverage

The project includes comprehensive unit tests for core functionality:

**RiskEvaluationTest** (~25 tests)
- Risk level determination (LOW/MEDIUM/HIGH) based on thresholds
- Threshold boundary conditions
- Risk action mapping (ALLOW/CHALLENGE/BLOCK)
- Fail mode behavior (FAIL_OPEN/FAIL_CLOSED)
- Edge cases (negative scores, values >100, same thresholds)

**MaxMindMinFraudServiceTest** (~12 tests)
- Service level switching (Score/Insights/Factors)
- API request building with IP, email, device session ID
- Error handling (invalid IP, HTTP exceptions, network errors)
- FraudCheckResult success and error scenarios

**ConfigurationParsingTest** (~27 tests)
- Configuration value parsing (integers, enums, booleans)
- Service level and risk action enum validation
- Threshold parsing and validation
- Timeout configuration parsing (default, custom, invalid)
- Missing/invalid configuration handling
- Boolean string parsing ("true"/"false")

**MaxMindMfaEnforcerAuthenticatorTest** (~20 tests)
- No challenge note scenarios (should allow)
- Challenge with user having OTP/WebAuthn (should allow)
- Challenge without MFA configured (should block)
- Custom MFA credential types configuration
- Configuration defaults and edge cases
- Event logging for ALLOWED/BLOCKED decisions
- Error page attributes for blocked users

### Testing Framework

- **JUnit 5** (Jupiter): Test framework
- **Mockito 5**: Mocking framework for MaxMind SDK
- **AssertJ**: Fluent assertions library

### What's Not Tested

- Full authenticator integration (requires extensive Keycloak mocking)
- Database operations (requires test database or EntityManager mocking)
- Entity getters/setters (minimal business logic)
- Factory instantiation (trivial)

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage (if jacoco is configured)
mvn test jacoco:report

# Run specific test suite
mvn test -Dtest=*Test
```

## Local Development Environment

Start Keycloak and PostgreSQL with Docker Compose:

```bash
# Build first
mvn clean package

# Start services
docker-compose up

# Access Keycloak at http://localhost:8080
# Admin credentials: admin / admin
# Database: postgres:5432, keycloak/keycloak/keycloak
```

The docker-compose setup automatically mounts the built JAR to `/opt/bitnami/keycloak/providers/` for live testing.

## Architecture Overview

### Keycloak SPI Integration Pattern

This extension uses Keycloak's Service Provider Interface (SPI) architecture with three main provider types:

1. **AuthenticatorFactory SPI** - Registers the fraud detection authenticator in Keycloak's authentication flow system
2. **Authenticator SPI** - Executes fraud checks during login
3. **Custom Storage Provider** - Persists fraud check results to database

### Component Flow

#### Post-Authentication Mode (Default)

```
User Login Attempt
    ↓
Username/Password Authentication
    ↓
MaxMindMinFraudAuthenticator.authenticate()
    ↓
Detect: user = available (post-auth mode)
    ↓
Collect: IP, email, username, device fingerprint (optional)
    ↓
MaxMindMinFraudService.checkFraud()
    ↓
MaxMind API (Score/Insights/Factors)
    ↓
Evaluate risk score against thresholds
    ↓
Take action: ALLOW / CHALLENGE / BLOCK
    ↓
MaxMindMinFraudCheckProvider.create()
    ↓
Store result in maxmind_minfraud_check table (user_id populated, is_pre_auth=false)
```

#### Pre-Authentication Mode (Optional)

```
User Login Attempt
    ↓
MaxMindMinFraudAuthenticator.authenticate()
    ↓
Detect: user = null (pre-auth mode)
    ↓
Collect: IP, session_id (no email/username available)
    ↓
MaxMindMinFraudService.checkFraud()
    ↓
MaxMind API (Score/Insights/Factors)
    ↓
Evaluate risk score against thresholds
    ↓
Take action: ALLOW / BLOCK (CHALLENGE supported but not recommended)
    ↓
MaxMindMinFraudCheckProvider.create()
    ↓
Store result (user_id=null, session_id populated, is_pre_auth=true)
    ↓
Username/Password Authentication
    ↓
MaxMindPreAuthCorrelatorAuthenticator.authenticate()
    ↓
Query database for pre-auth checks by session_id
    ↓
Update records: set user_id, username, email, correlated_at
```

### Key Components

**MaxMindMinFraudAuthenticator** - Core authenticator logic
- `authenticate()`: Entry point, handles device tracking form presentation
- `action()`: Processes form submission and performs fraud check
- `performFraudCheck()`: Orchestrates API call, risk evaluation, and action handling (supports both pre-auth and post-auth modes)
- `handleRiskAction()`: Executes ALLOW/CHALLENGE/BLOCK decisions (sets different auth notes for pre-auth vs post-auth)
- `storeFraudCheck()`: Persists results via provider (includes session_id and is_pre_auth flag)
- `requiresUser()`: Returns false to enable hybrid mode (auto-detects pre-auth vs post-auth based on user availability)

**MaxMindMinFraudAuthenticatorFactory** - Configuration and registration
- Defines all configuration properties visible in Keycloak admin UI
- Creates singleton authenticator instance
- Registers as `maxmind-minfraud-authenticator` provider

**MaxMindPreAuthCorrelatorAuthenticator** - Pre-auth correlation logic
- `authenticate()`: Links pre-auth fraud checks with user accounts after login
- Queries database for pre-auth checks by session_id
- Updates records with user_id, username, email, correlated_at
- Logs correlation events for audit trail
- Always calls `context.success()` to avoid blocking authentication
- `requiresUser()`: Returns true (must run after user identification)

**MaxMindPreAuthCorrelatorAuthenticatorFactory** - Correlator registration
- Registers as `maxmind-preauth-correlator` provider
- No configuration properties (works automatically)
- Displays in admin UI as "MaxMind Pre-Auth Correlator"

**MaxMindMfaEnforcerAuthenticator** - MFA requirement enforcer
- `authenticate()`: Checks for CHALLENGE auth note and verifies MFA credentials
- Supports both post-auth (`maxmind_challenge`) and pre-auth (`maxmind_preauth_challenge`) modes
- Blocks users without MFA when CHALLENGE is triggered
- Allows users with MFA to proceed to verification
- Supports configurable credential types (otp, webauthn, sms-otp, etc.)
- Logs mode (PRE_AUTH/POST_AUTH) in event details for tracking

**MaxMindMfaEnforcerAuthenticatorFactory** - MFA enforcer registration
- Registers as `maxmind-mfa-enforcer` provider
- Configures `mfaCredentialTypes` property (default: "otp,webauthn")
- Displays in admin UI as "MaxMind MFA Enforcer"

**MaxMindMinFraudService** - MaxMind API wrapper
- Supports three service levels: SCORE, INSIGHTS, FACTORS
- Uses official MaxMind Java SDK (WebServiceClient)
- Returns FraudCheckResult with risk score or error

**MaxMindMinFraudCheckProvider/Factory** - Data persistence
- JPA-based storage using Keycloak's EntityManager
- Named queries for common lookups (by user, realm, date range, risk level)
- Factory registered as custom provider in Keycloak

**MaxMindMinFraudCheckEntity** - JPA entity
- Table: `maxmind_minfraud_check`
- Stores: risk_score, decision, raw_response, device_session_id, user_id (nullable), session_id, is_pre_auth, correlated_at
- Indexes on user_id, realm_id, timestamp, session_id for analytics and pre-auth correlation
- Named query `findBySessionIdPreAuth` for pre-auth correlation lookup
- Schema version 1.2.0 includes pre-authentication support

### SPI Registration

SPI providers are registered via files in `src/main/resources/META-INF/services/`:

- `org.keycloak.authentication.AuthenticatorFactory` → MaxMindMinFraudAuthenticatorFactory, MaxMindMfaEnforcerAuthenticatorFactory, MaxMindPreAuthCorrelatorAuthenticatorFactory
- `org.keycloak.provider.Spi` → MaxMindMinFraudCheckProviderFactory

These files enable Keycloak's service loader to discover and load the extension.

### Database Schema Management

Liquibase handles schema creation via `src/main/resources/META-INF/maxmind-minfraud-changelog.xml`:
- Automatically creates `maxmind_minfraud_check` table on first deployment
- Manages indexes and schema evolution
- Keycloak executes Liquibase changesets during startup
- **Schema 1.2.0 changes** (pre-auth support):
  - Made `user_id` nullable to support pre-auth checks
  - Added `session_id` column for session-based correlation
  - Added `is_pre_auth` boolean flag to identify pre-auth checks
  - Added `correlated_at` timestamp for tracking correlation timing
  - Added indexes on `session_id` and `(is_pre_auth, session_id)` for efficient lookups

### Device Tracking Implementation

When enabled, device tracking works without custom themes:
1. Authenticator injects JavaScript via `LoginFormsProvider.setAttribute()`
2. JavaScript loads MaxMind Device Tracking SDK from CDN
3. Device session ID captured client-side and added to form as hidden field
4. Session ID sent to MaxMind API in `device.session_id` field
5. MaxMind correlates device across multiple login attempts

### Risk-Based Actions

The authenticator evaluates risk scores against two thresholds to determine three risk levels:

- **Low Risk** (score ≤ lowRiskThreshold): Execute lowRiskAction
- **Medium Risk** (lowRiskThreshold < score ≤ highRiskThreshold): Execute mediumRiskAction
- **High Risk** (score > highRiskThreshold): Execute highRiskAction

Actions map to Keycloak authentication flow states:
- `ALLOW`: `context.success()` - Continue to next authenticator (generates LOGIN event)
- `CHALLENGE`: `context.success()` + set auth notes - Allow authentication but signal need for MFA (generates LOGIN event)
- `BLOCK`: `context.failure()` - Deny authentication with error page (generates LOGIN_ERROR event)

**CHALLENGE Action Implementation:**

When CHALLENGE action is triggered, the authenticator:
1. Calls `context.success()` to allow the authentication flow to continue
2. Sets authentication session notes for conditional authenticators to check:
   - **Post-Auth Mode**: `maxmind_challenge` = "true" and `maxmind_risk_score` = risk score value
   - **Pre-Auth Mode**: `maxmind_preauth_challenge` = "true" and `maxmind_preauth_risk_score` = risk score value
3. Logs the action to Keycloak events with `maxmind_minfraud_action=CHALLENGE`

**Important**: CHALLENGE action in pre-auth mode is **not recommended** because the user hasn't been identified yet. Pre-auth mode is best used with ALLOW/BLOCK actions.

**MFA Enforcement for CHALLENGE Actions:**

The extension includes **MaxMindMfaEnforcerAuthenticator** which enforces MFA requirement when CHALLENGE is triggered:

1. **Checks for CHALLENGE**: Reads `maxmind_challenge` (post-auth) or `maxmind_preauth_challenge` (pre-auth) auth note
2. **Verifies MFA Configuration**: Checks if user has at least one configured MFA credential
3. **Blocks Users Without MFA**: Prevents OTP setup during suspicious logins by calling `context.failure()`
4. **Allows Users With MFA**: Lets users proceed to MFA verification step
5. **Logs Mode**: Records PRE_AUTH or POST_AUTH mode in event details for tracking

**Configuration**:
- `mfaCredentialTypes`: Comma-separated list of credential types (default: "otp,webauthn")
- Supports: otp (TOTP/HOTP), webauthn (passkeys), sms-otp (if extension installed)

**Authentication Flow Structure**:
```
Browser Forms
├── Username Password Form (REQUIRED)
├── MaxMind minFraud (REQUIRED) ← Assesses risk, sets challenge note
├── MaxMind MFA Enforcer (REQUIRED) ← Enforces MFA requirement
└── Conditional OTP (CONDITIONAL) ← Presents MFA challenge
    ├── conditional-user-configured (REQUIRED)
    └── OTP Form (REQUIRED)
```

**Key Implementation Details**:
- **MaxMindMfaEnforcerAuthenticator**: Regular authenticator (not conditional) that can block authentication
- Uses `user.credentialManager().isConfiguredFor(type)` to check MFA credentials
- Logs detailed events: `maxmind_mfa_enforcer_decision` (ALLOWED/BLOCKED), `maxmind_mfa_enforcer_type`, `maxmind_mfa_enforcer_risk_score`
- Error message key: `mfaRequiredForSuspiciousActivity`

**Why Not Conditional Authenticator?**
- ConditionalAuthenticator can only return true/false (cannot call `context.failure()`)
- Regular authenticator allows blocking users without MFA during suspicious logins
- Provides better security by preventing attackers from gaining access by simply setting up OTP during fraud attempt

### Error Handling Strategy

Two fail modes control behavior when MaxMind API fails:

**FAIL_OPEN** (default):
- API error → log error, store record with error_message, call `context.success()`
- Prioritizes availability over security
- Use in development or non-critical applications

**FAIL_CLOSED**:
- API error → log error, store record with error_message, call `context.failure()`
- Prioritizes security over availability
- Use in production/high-security environments

### API Timeouts

Configurable timeouts prevent users from waiting indefinitely if MaxMind API is slow:

**Connection Timeout** (default 3000ms):
- Maximum time to establish TCP connection to MaxMind API
- Applied via `WebServiceClient.Builder.connectTimeout(Duration.ofMillis(...))`

**Read Timeout** (default 5000ms):
- Maximum time to wait for API response after connection established
- Applied via `WebServiceClient.Builder.readTimeout(Duration.ofMillis(...))`

**Worst-case delay**: Connection timeout + Read timeout = 8 seconds (with defaults)

**On timeout**:
1. MaxMind SDK throws IOException or HttpException
2. `MaxMindMinFraudService.checkFraud()` catches exception and returns error result
3. Authenticator logs error and stores record with `error_message` set
4. Configured fail mode determines whether to ALLOW (FAIL_OPEN) or BLOCK (FAIL_CLOSED)

**Service constructor signature**:
```java
new MaxMindMinFraudService(accountId, licenseKey, serviceLevel, connectTimeoutMs, readTimeoutMs)
```

### Event Logging

The extension implements a **dual logging strategy** to provide both real-time audit trails and long-term analytics:

1. **Database Table** (`maxmind_minfraud_check`): Long-term storage for fraud analytics
2. **Keycloak Events** (`event_entity`): Short-term audit trail for compliance and real-time monitoring

#### Event Implementation

Events are logged using Keycloak's EventBuilder API accessed via `context.getEvent()`:

**After successful fraud check** (`MaxMindMinFraudAuthenticator.performFraudCheck()` ~line 174):
```java
context.getEvent()
    .detail("maxmind_minfraud_risk_score", String.format("%.2f", riskScore))
    .detail("maxmind_minfraud_risk_level", riskLevel)
    .detail("maxmind_minfraud_decision", action.name())
    .detail("maxmind_minfraud_request_id", result.getRequestId())
    .detail("maxmind_minfraud_service_level", serviceLevel.name())
    .detail(Details.AUTH_METHOD, "maxmind_minfraud");
```

**On API failure** (`MaxMindMinFraudAuthenticator.performFraudCheck()` ~line 195):
```java
context.getEvent()
    .detail("maxmind_minfraud_error", result.getErrorMessage())
    .detail("maxmind_minfraud_decision", "ERROR")
    .detail("maxmind_minfraud_service_level", serviceLevel.name())
    .detail(Details.AUTH_METHOD, "maxmind_minfraud");
```

**On CHALLENGE action** (`MaxMindMinFraudAuthenticator.handleRiskAction()` ~line 246):
```java
context.getEvent()
    .detail("maxmind_minfraud_action", "CHALLENGE")
    .detail("maxmind_minfraud_risk_score", String.format("%.2f", riskScore));
```

**On BLOCK action** (`MaxMindMinFraudAuthenticator.handleRiskAction()` ~line 256):
```java
context.getEvent()
    .detail("maxmind_minfraud_action", "BLOCK")
    .detail("maxmind_minfraud_risk_score", String.format("%.2f", riskScore));
```

**On configuration error** (`MaxMindMinFraudAuthenticator.handleConfigurationError()` ~line 320):
```java
context.getEvent()
    .detail("maxmind_minfraud_error", "Configuration error")
    .detail("maxmind_minfraud_decision", "ERROR")
    .detail(Details.AUTH_METHOD, "maxmind_minfraud");
```

#### Event Detail Keys

All custom event detail keys use the `maxmind_minfraud_` prefix (as explicitly required):

| Key | Type | Description |
|-----|------|-------------|
| `maxmind_minfraud_risk_score` | String (decimal) | Risk score from MaxMind (0-100) |
| `maxmind_minfraud_risk_level` | String | Risk level: LOW, MEDIUM, HIGH |
| `maxmind_minfraud_decision` | String | Action taken: ALLOW, CHALLENGE, BLOCK, ERROR |
| `maxmind_minfraud_request_id` | String (UUID) | MaxMind API request ID for correlation |
| `maxmind_minfraud_service_level` | String | Service level used: SCORE, INSIGHTS, FACTORS |
| `maxmind_minfraud_action` | String | Specific action: CHALLENGE, BLOCK |
| `maxmind_minfraud_error` | String | Error message if API failed |
| `auth_method` | String | Always "maxmind_minfraud" (standard Keycloak key) |

#### Testing Event Logging

**Enable event storage** in Keycloak:
1. Navigate to Realm Settings → Events → User Events Settings
2. Enable "Save Events"
3. Set expiration (e.g., 30 days)

**Query events from database**:
```sql
-- View fraud check events
SELECT time, user_id, ip_address, details
FROM event_entity
WHERE details LIKE '%maxmind_minfraud%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 day') * 1000
ORDER BY time DESC
LIMIT 10;

-- Correlate events with fraud check records
SELECT
    to_timestamp(e.time / 1000) as event_time,
    e.details as event_details,
    f.risk_score,
    f.decision,
    f.request_id
FROM event_entity e
INNER JOIN maxmind_minfraud_check f
    ON e.user_id = f.user_id
    AND e.details LIKE '%' || f.request_id || '%'
WHERE f.request_id IS NOT NULL
  AND e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 day') * 1000
ORDER BY e.time DESC;
```

**View events in Admin Console**:
- Navigate to: Realm Settings → Events → User Events
- Filter by user or date range
- Click event to see all details including fraud check data

## Configuration Structure

All configuration is per-realm via AuthenticatorConfigModel stored in Keycloak database:

```java
Map<String, String> config = {
  "accountId": "123456",
  "licenseKey": "secret",
  "serviceLevel": "SCORE|INSIGHTS|FACTORS",
  "deviceTrackingEnabled": "true|false",
  "lowRiskThreshold": "0-100",
  "highRiskThreshold": "0-100",
  "lowRiskAction": "ALLOW|CHALLENGE|BLOCK",
  "mediumRiskAction": "ALLOW|CHALLENGE|BLOCK",
  "highRiskAction": "ALLOW|CHALLENGE|BLOCK",
  "failMode": "FAIL_OPEN|FAIL_CLOSED",
  "connectTimeout": "milliseconds (default: 3000)",
  "readTimeout": "milliseconds (default: 5000)"
}
```

Access in authenticator via:
```java
AuthenticatorConfigModel config = context.getAuthenticatorConfig();
String accountId = config.getConfig().get(CONFIG_ACCOUNT_ID);
```

## Important Naming Conventions

All database entities, tables, and classes use the `MaxMindMinFraud` prefix (not just `MaxMind` or `MinFraud`):
- Class: `MaxMindMinFraudCheckEntity`
- Table: `maxmind_minfraud_check`
- Provider: `MaxMindMinFraudCheckProvider`

This prevents naming conflicts with potential future MaxMind GeoIP or other integrations.

## Testing Fraud Detection

Query recent fraud checks:
```sql
SELECT timestamp, username, ip_address, risk_score, decision
FROM maxmind_minfraud_check
ORDER BY timestamp DESC LIMIT 10;
```

Enable debug logging in Keycloak:
```
logger.maxmind.level=DEBUG
logger.maxmind.name=com.zymlabs.keycloak.maxmindprovider
```

Test scenarios:
- Normal login: Low risk score (0-30), ALLOWED
- VPN/proxy: Medium-high risk score (40-80), CHALLENGED or BLOCKED
- Invalid credentials: Error logged, behavior depends on failMode

## Deployment

Deploy to Keycloak Quarkus (v17+):
```bash
cp target/zymlabs-maxmind-provider.jar /path/to/keycloak/providers/
bin/kc.sh build
bin/kc.sh start
```

Verify deployment in logs:
```
INFO  [com.zymlabs.keycloak.maxmindprovider.MaxMindMinFraudCheckProviderFactory] Initializing MaxMindMinFraudCheckProviderFactory
```

## Key Dependencies

- Keycloak: 24.0.0+ (provided scope - not bundled in JAR)
- Java: 17+ required
- MaxMind minFraud SDK: 1.16.0 (bundled)
- Jackson: 2.15.3 (for JSON serialization of raw responses)
- Jakarta EE APIs: jakarta.ws.rs, jakarta.persistence (provided by Keycloak 24+)

All Keycloak dependencies use `provided` scope since they're available in the Keycloak runtime.

### Version Compatibility

- **v1.0+**: Keycloak 24+ (Jakarta EE), Java 17+ - **RECOMMENDED** (fixes Admin UI NullPointerException bug)
- **v0.9**: Keycloak 22.x (has known Admin UI bug - upgrade to 24+ recommended)
- **v0.1-SNAPSHOT**: Keycloak 17-21 (Java EE), Java 11+

**Important**: Keycloak 22.0.x has a known bug causing `NullPointerException` when accessing the Authentication section in the Admin Console. This was fixed in Keycloak 24.0.2+. If you encounter this error, upgrade to Keycloak 24.0.5 or later.

## Documentation

Comprehensive documentation in `docs/`:
- `SETUP.md` - Installation and MaxMind account setup
- `CONFIGURATION.md` - All configuration options with examples
- `DATABASE.md` - Schema details and analytics queries
- `API.md` - MaxMind API integration details
- `TROUBLESHOOTING.md` - Common issues and solutions

Example configurations in `examples/`:
- `maxmind-authenticator-config.json` - Configuration templates
- `browser-flow-with-maxmind.json` - Authentication flow structure
- We use conventional commit standard for commit messages