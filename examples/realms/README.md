# Keycloak Realm Auto-Import Configuration

This directory contains a Keycloak realm export that is automatically imported when Keycloak starts via docker-compose. The realm includes test users, pre-configured authentication flows with the MaxMind minFraud authenticator, and sensible default settings.

## Quick Start

### 1. Build and Start Keycloak

From the project root directory:

```bash
# Build the extension JAR
mvn clean package

# Start Keycloak and PostgreSQL
docker-compose up -d

# Watch the logs to verify import
docker-compose logs -f keycloak
```

Look for these log messages confirming successful import:
```
INFO  [org.keycloak.services] KC-SERVICES0050: Initializing maxmind-demo realm
```

### 2. Update MaxMind Credentials

Before the MaxMind authenticator will work, you need to update the placeholder credentials in the realm JSON:

1. Open `examples/realms/maxmind-demo-realm.json`
2. Find the `authenticatorConfig` section (around line 216)
3. Replace the placeholder values:
   ```json
   "accountId": "YOUR_MAXMIND_ACCOUNT_ID",  ← Replace with your actual account ID
   "licenseKey": "YOUR_MAXMIND_LICENSE_KEY"  ← Replace with your actual license key
   ```
4. Save the file
5. If Keycloak is already running, you'll need to re-import (see "Re-importing After Changes" below)

**Note**: For testing purposes, you can skip this step initially. With `failMode: "FAIL_OPEN"`, authentication will succeed even if MaxMind API calls fail due to invalid credentials. However, no fraud detection will occur.

### 3. Access the Realm

**Keycloak Admin Console:**
```
URL: http://localhost:8080/admin
Username: admin
Password: admin
```

Switch to the `maxmind-demo` realm using the dropdown in the top-left corner.

**Account Console (for testing login flow):**
```
URL: http://localhost:8080/realms/maxmind-demo/account
```

### 4. Verify MaxMind Authentication Flow

The realm automatically imports with a pre-configured authentication flow called **"browser with maxmind"** that includes the MaxMind fraud detection authenticator.

You can verify this in the Admin Console:

1. Navigate to: **Authentication** → **Flows**
2. Select "browser with maxmind" from the dropdown
3. You should see the flow structure:
   - Cookie authentication (ALTERNATIVE)
   - Identity Provider Redirector (ALTERNATIVE)
   - **browser with maxmind forms** (ALTERNATIVE) - subflow containing:
     - Username Password Form (REQUIRED)
     - **MaxMind minFraud** (REQUIRED) ← The fraud detection authenticator
     - Conditional OTP (CONDITIONAL)
4. Go to **Authentication** → **Bindings**
5. Verify that **Browser Flow** is set to "browser with maxmind"

**Optional**: Click the **⚙️** (settings) icon next to "MaxMind minFraud" to review or adjust the configuration (thresholds, timeouts, etc.).

### 5. Test the MaxMind Authenticator

Use one of the pre-configured test users:

| Username   | Password  | Purpose                    |
|------------|-----------|----------------------------|
| testuser   | password  | General testing            |
| demouser   | demo123   | Alternative test account   |

**Testing steps:**

1. Open an **incognito/private browser window** (to ensure fresh session)
2. Navigate to: `http://localhost:8080/realms/maxmind-demo/account`
3. Log in with `testuser` / `password`
4. The MaxMind fraud check will execute automatically after username/password validation
5. Depending on your IP's risk score, you'll either:
   - **Low risk (< 30)**: Login succeeds immediately
   - **Medium risk (30-70)**: Prompted for additional authentication (if configured)
   - **High risk (> 70)**: Login blocked with error message

### 5. Verify Fraud Check Execution

**Check the database:**

```sql
-- Connect to PostgreSQL
docker exec -it keycloak-maxmind-postgres-1 psql -U keycloak

-- Query fraud check records
SELECT
    timestamp,
    username,
    ip_address,
    risk_score,
    decision,
    request_id
FROM maxmind_minfraud_check
ORDER BY timestamp DESC
LIMIT 5;
```

**Check Keycloak events:**

1. Navigate to: Admin Console → Realm Settings → Events → User Events
2. Look for recent LOGIN events
3. Click on an event to see details including:
   - `maxmind_minfraud_risk_score`
   - `maxmind_minfraud_risk_level`
   - `maxmind_minfraud_decision`
   - `maxmind_minfraud_request_id`

**Check logs:**

```bash
# Watch for MaxMind-related logs
docker-compose logs -f keycloak | grep -i maxmind
```

## What Gets Imported

### Realm: maxmind-demo

The realm JSON automatically imports:

- **Realm Settings**
  - Brute force protection enabled
  - Login with email allowed
  - Password reset enabled
  - Event logging enabled for LOGIN, LOGOUT, REGISTER events

- **Test Users** (ready to use immediately):
  - `testuser` / `password`
  - `demouser` / `demo123`

- **Authentication Flows** (pre-configured):
  - **"browser with maxmind"** - Custom browser authentication flow with MaxMind fraud detection
  - **"browser with maxmind forms"** - Subflow containing username/password + MaxMind authenticator
  - **"browser with maxmind Browser - Conditional OTP"** - Conditional OTP subflow for MFA challenges
  - Browser flow binding set to "browser with maxmind"

- **MaxMind Authenticator Configuration**:
  - Account ID: `YOUR_MAXMIND_ACCOUNT_ID` (placeholder - update before use)
  - License Key: `YOUR_MAXMIND_LICENSE_KEY` (placeholder - update before use)
  - Service Level: SCORE
  - Device Tracking: Disabled
  - Low Risk Threshold: 30
  - High Risk Threshold: 70
  - Risk Actions: ALLOW (low), CHALLENGE (medium), BLOCK (high)
  - Fail Mode: FAIL_OPEN (allows authentication if API fails)
  - Timeouts: 3000ms connect, 5000ms read

**Default Clients** (automatically available):
- `account-console` - Modern account management UI for testing
- `account` - Legacy account console
- `security-admin-console` - Realm-specific admin console

## Customizing the MaxMind Authenticator

You can adjust the pre-configured MaxMind authenticator settings at any time through the Admin Console:

### Adjust Risk Thresholds

1. Go to **Authentication** → **Flows**
2. Select the `browser with maxmind` flow
3. Click **⚙️** next to "MaxMind minFraud"
4. Adjust thresholds:
   - **Low Risk Threshold** (0-100): Users below this score → Low Risk Action
   - **High Risk Threshold** (0-100): Users above this score → High Risk Action
   - Users between thresholds → Medium Risk Action

**Available actions:**
- `ALLOW` - Continue to next authenticator or grant access
- `CHALLENGE` - Trigger conditional flows (e.g., step-up to MFA)
- `BLOCK` - Deny authentication with error message

### Change Service Level

In the authenticator configuration, set **Service Level**:
- **SCORE**: Fastest, returns risk score only
- **INSIGHTS**: Adds device, location, and email insights
- **FACTORS**: Full details including subscores for each risk factor

Higher service levels provide more data but cost more per API call.

### Enable Device Tracking

Set **Device Tracking Enabled** to `true` in the authenticator configuration.

When enabled, MaxMind can correlate login attempts across the same device for better risk assessment. Requires adding MaxMind's device tracking JavaScript to your login page (see main documentation).

### Adjust Fail Mode

Set **Fail Mode** in the authenticator configuration:
- **FAIL_OPEN**: If MaxMind API fails, allow login (prioritize availability)
- **FAIL_CLOSED**: If MaxMind API fails, block login (prioritize security)

Choose based on your security requirements and MaxMind API reliability.

## Re-importing After Changes

**Important**: Keycloak only imports realms that don't already exist. If you modify the JSON and want to re-import:

### Option 1: Delete the existing realm

```bash
# Via Admin Console:
# 1. Login to http://localhost:8080/admin
# 2. Select "maxmind-demo" realm
# 3. Realm Settings → Actions → Delete realm

# Then restart:
docker-compose restart keycloak
```

### Option 2: Use a different realm name

Change the `realm` field in the JSON:

```json
{
  "realm": "maxmind-demo-v2",  ← New name
  ...
}
```

Then restart Keycloak.

### Option 3: Delete PostgreSQL data (nuclear option)

```bash
# WARNING: This deletes ALL Keycloak data including master realm
docker-compose down -v
docker-compose up -d
```

## Exporting a Modified Realm

After customizing your realm via the Admin Console, you can export it:

### Via Admin Console (Partial Export)

1. Navigate to: Realm Settings → Actions → Partial Export
2. Select what to include (users, clients, roles, etc.)
3. Download the JSON file

**Note**: Partial exports may not include everything needed for a clean import.

### Via CLI (Full Export)

```bash
# Enter the Keycloak container
docker exec -it keycloak-maxmind-keycloak-1 bash

# Export the realm
/opt/bitnami/keycloak/bin/kc.sh export \
  --dir /tmp \
  --realm maxmind-demo \
  --users realm_file

# Exit and copy the file out
exit
docker cp keycloak-maxmind-keycloak-1:/tmp/maxmind-demo-realm.json ./maxmind-demo-realm-backup.json
```

## Security Warnings

⚠️ **FOR DEVELOPMENT/TESTING ONLY**

This realm configuration includes:

1. **Plaintext MaxMind credentials** in the JSON file
   - Never commit real credentials to version control
   - Use environment variables or secret management in production

2. **Hardcoded test user passwords**
   - `testuser` / `password` is obviously insecure
   - These accounts should only exist in dev/test environments

3. **Weak password policies**
   - No complexity requirements
   - No password expiration
   - Configure proper policies in production

4. **Event logging without log shipping**
   - Events are stored in PostgreSQL with 30-day retention
   - Production should ship logs to a SIEM or log aggregation platform

5. **HTTP-only configuration**
   - No TLS/HTTPS configured
   - Production must use HTTPS with valid certificates

**For production deployments:**
- Use proper secret management (HashiCorp Vault, AWS Secrets Manager, etc.)
- Enable TLS/HTTPS everywhere
- Configure strong password policies
- Set up log shipping and monitoring
- Use FAIL_CLOSED mode for critical applications
- Enable brute force protection
- Configure rate limiting

## Troubleshooting

### Realm doesn't import

**Check logs:**
```bash
docker-compose logs keycloak | grep -i "import\|realm\|error"
```

**Common issues:**
- Realm with same name already exists → Delete it or rename in JSON
- JSON syntax error → Validate with `jq . maxmind-demo-realm.json`
- Missing directory mount → Verify `docker-compose.yml` has volume mount

### MaxMind authenticator doesn't execute

1. **Verify extension is loaded:**
   ```bash
   docker-compose logs keycloak | grep -i "maxmind"
   ```
   Should see: `KC-SERVICES0047: maxmind-minfraud-authenticator`

2. **Check browser flow binding:**
   - Admin Console → Authentication → Bindings
   - "Browser Flow" should be set to `browser with maxmind`

3. **Verify authenticator is in the flow:**
   - Admin Console → Authentication → Flows
   - Select `browser with maxmind`
   - Should see `maxmind-minfraud-authenticator` execution

4. **Check credentials are configured:**
   - Admin Console → Authentication → Flows → `browser with maxmind`
   - Click ⚙️ (settings) next to `maxmind-minfraud-authenticator`
   - Verify Account ID and License Key are not the placeholder values (`YOUR_MAXMIND_ACCOUNT_ID`)

### MaxMind API errors

**Check logs:**
```bash
docker-compose logs keycloak | grep -i "maxmind\|fraud"
```

**Common errors:**

- `AuthenticationException: Your account ID or license key could not be authenticated`
  - Wrong credentials → Verify and update in realm JSON

- `HttpException: Timeout`
  - MaxMind API is slow/unreachable → Check network connectivity
  - Increase timeouts: `connectTimeout` and `readTimeout`

- `InsufficientFundsException: Out of credits`
  - Your MaxMind account has no credits → Add credits or use different account

### Database connection issues

```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# Check Keycloak can connect
docker-compose logs keycloak | grep -i "postgres\|database"

# Manually test connection
docker exec -it keycloak-maxmind-postgres-1 psql -U keycloak -d keycloak -c "SELECT 1;"
```

## Further Documentation

- **Main setup guide**: `../../docs/SETUP.md`
- **Configuration reference**: `../../docs/CONFIGURATION.md`
- **Database schema**: `../../docs/DATABASE.md`
- **Troubleshooting**: `../../docs/TROUBLESHOOTING.md`
- **MaxMind API docs**: https://dev.maxmind.com/minfraud

## Testing Different Risk Scenarios

### Simulate Low Risk (Allow)

Normal login from your regular IP should result in low risk scores (0-30).

### Simulate Medium Risk (Challenge)

Options:
1. Use a VPN to change your IP to a different country
2. Temporarily lower thresholds: `"lowRiskThreshold": "0"`
3. Use an email address associated with fraud (use a disposable email service)

Expected: User is prompted for MFA (if configured), or login succeeds if no MFA.

### Simulate High Risk (Block)

Options:
1. Use a known VPN or proxy IP
2. Temporarily set thresholds: `"lowRiskThreshold": "0"`, `"highRiskThreshold": "0"`
3. Multiple failed login attempts before success

Expected: Login is blocked with error message.

### Test API Failure (Fail Modes)

Temporarily use invalid credentials to trigger API errors:

```json
"accountId": "000000",
"licenseKey": "invalid_key"
```

- With `FAIL_OPEN`: Login succeeds despite error
- With `FAIL_CLOSED`: Login is blocked due to error

**Remember to restore correct credentials afterward!**

## Support

For issues with:
- **This realm configuration**: Open an issue in this repository
- **MaxMind API**: Contact MaxMind support at https://support.maxmind.com/
- **Keycloak itself**: See Keycloak documentation at https://www.keycloak.org/documentation
