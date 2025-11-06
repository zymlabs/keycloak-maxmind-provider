# Example Configurations

This directory contains example configurations for the MaxMind minFraud Keycloak extension.

## Files

### Post-Authentication Mode (Recommended)

- **browser-flow-with-maxmind.json** - Authentication flow with post-auth fraud detection
- **maxmind-authenticator-config.json** - Configuration for post-auth mode

### Pre-Authentication Mode (Advanced)

- **browser-flow-preauth.json** - Main browser flow with pre-auth fraud detection
- **browser-flow-preauth-forms.json** - Forms subflow for pre-auth mode
- **browser-flow-preauth-otp.json** - OTP conditional subflow
- **maxmind-preauth-config.json** - Configuration for pre-auth mode

## Post-Authentication Mode

### browser-flow-with-maxmind.json

Example authentication flow that includes MaxMind fraud detection **after** username/password authentication.

**Structure**:
```
Browser with MaxMind
├── Cookie (ALTERNATIVE)
├── Kerberos (DISABLED)
├── Identity Provider Redirector (ALTERNATIVE)
└── Browser with MaxMind Forms (ALTERNATIVE)
    ├── Username Password Form (REQUIRED)
    ├── MaxMind minFraud (REQUIRED)
    └── Conditional OTP (CONDITIONAL)
        ├── Condition - User Configured (REQUIRED)
        └── OTP Form (REQUIRED)
```

**Import Instructions**:

1. Log into Keycloak Admin Console
2. Navigate to **Authentication** → **Flows**
3. Click **Copy** on the "Browser" flow
4. Name it "Browser with MaxMind"
5. Add executions manually following the structure above, OR
6. Use the Keycloak REST API to import the flow

### maxmind-authenticator-config.json

Example authenticator configuration with recommended settings.

**Before Using**:
- Replace `YOUR_ACCOUNT_ID` with your MaxMind account ID
- Replace `YOUR_LICENSE_KEY` with your MaxMind license key

**Configuration Values**:

| Setting | Example Value | Description |
|---------|---------------|-------------|
| accountId | 123456 | Your MaxMind account ID |
| licenseKey | xK7j...9Lm2 | Your MaxMind license key |
| serviceLevel | SCORE | API service level (SCORE, INSIGHTS, FACTORS) |
| deviceTrackingEnabled | false | Enable/disable device fingerprinting |
| lowRiskThreshold | 30 | Maximum score for low risk (0-30) |
| highRiskThreshold | 70 | Minimum score for high risk (71-100) |
| lowRiskAction | ALLOW | Action for low risk: ALLOW, CHALLENGE, BLOCK |
| mediumRiskAction | CHALLENGE | Action for medium risk (31-70) |
| highRiskAction | BLOCK | Action for high risk (71-100) |
| failMode | FAIL_OPEN | FAIL_OPEN (allow on error) or FAIL_CLOSED (block on error) |

**Import Instructions**:

1. Create your authentication flow (see above)
2. Add MaxMind minFraud execution
3. Click Actions → Config
4. Enter the configuration values from this file (with your credentials)
5. Click Save

## Configuration Scenarios

### Development/Testing Configuration

Use this for development and initial testing:

```json
{
  "serviceLevel": "SCORE",
  "deviceTrackingEnabled": "false",
  "lowRiskThreshold": "30",
  "highRiskThreshold": "70",
  "lowRiskAction": "ALLOW",
  "mediumRiskAction": "ALLOW",
  "highRiskAction": "ALLOW",
  "failMode": "FAIL_OPEN"
}
```

**Result**: All logins proceed. Data collected for analysis.

### Balanced Production Configuration

Recommended for most production deployments:

```json
{
  "serviceLevel": "INSIGHTS",
  "deviceTrackingEnabled": "true",
  "lowRiskThreshold": "30",
  "highRiskThreshold": "70",
  "lowRiskAction": "ALLOW",
  "mediumRiskAction": "CHALLENGE",
  "highRiskAction": "BLOCK",
  "failMode": "FAIL_OPEN"
}
```

**Result**: Low risk proceeds, medium risk requires MFA, high risk blocked.

### High Security Configuration

For maximum security (financial, healthcare, etc.):

```json
{
  "serviceLevel": "FACTORS",
  "deviceTrackingEnabled": "true",
  "lowRiskThreshold": "20",
  "highRiskThreshold": "50",
  "lowRiskAction": "ALLOW",
  "mediumRiskAction": "CHALLENGE",
  "highRiskAction": "BLOCK",
  "failMode": "FAIL_CLOSED"
}
```

**Result**: Very strict thresholds. Most users will need MFA. API errors block login.

### User-Friendly Configuration

For consumer-facing applications prioritizing UX:

```json
{
  "serviceLevel": "SCORE",
  "deviceTrackingEnabled": "true",
  "lowRiskThreshold": "40",
  "highRiskThreshold": "80",
  "lowRiskAction": "ALLOW",
  "mediumRiskAction": "ALLOW",
  "highRiskAction": "CHALLENGE",
  "failMode": "FAIL_OPEN"
}
```

**Result**: Most users proceed without friction. Only very high risk gets MFA.

## Testing the Configuration

### 1. Test Normal Login

```bash
# Should proceed without issues (low risk score)
curl -X POST 'http://localhost:8080/realms/myrealm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'username=testuser' \
  -d 'password=password' \
  -d 'grant_type=password' \
  -d 'client_id=myclient'
```

Expected: Login succeeds, risk score 0-30

### 2. Verify in Database

```sql
SELECT * FROM maxmind_minfraud_check
ORDER BY timestamp DESC
LIMIT 1;
```

Expected: Record with decision=ALLOWED

### 3. Test with VPN/Proxy

Connect through a VPN and test login.

Expected: Higher risk score, possibly CHALLENGED or BLOCKED

### 4. Test API Error Handling

Temporarily use invalid credentials in config.

Expected (FAIL_OPEN): Login proceeds, error logged
Expected (FAIL_CLOSED): Login blocked, error page shown

### 5. Test Device Tracking

1. Enable device tracking in config
2. Open browser console (F12)
3. Attempt login
4. Look for "MaxMind Device Session ID" in console
5. Check database for device_session_id

## Pre-Authentication Mode

### Overview

Pre-authentication mode runs fraud checks **before** username/password authentication to block suspicious IPs early.

### browser-flow-preauth.json, browser-flow-preauth-forms.json, browser-flow-preauth-otp.json

**Structure**:
```
Browser with MaxMind Pre-Auth
├── Cookie (ALTERNATIVE)
├── Kerberos (DISABLED)
├── Identity Provider Redirector (ALTERNATIVE)
└── Browser with MaxMind Pre-Auth Forms (ALTERNATIVE)
    ├── MaxMind minFraud (REQUIRED)              ← Runs BEFORE login
    ├── Username Password Form (REQUIRED)
    ├── MaxMind Pre-Auth Correlator (REQUIRED)   ← Links check to user
    └── Conditional OTP (CONDITIONAL)
        ├── Condition - User Configured (REQUIRED)
        └── OTP Form (REQUIRED)
```

**When to Use**:
- High bot/brute-force attack volume
- Protecting against credential stuffing
- Want to block known malicious IPs immediately
- Security prioritized over accuracy

**Key Differences from Post-Auth**:
1. **No User Context**: Email/username not available for MaxMind API
2. **Lower Accuracy**: Risk scores less accurate without user data
3. **Requires Correlator**: Must add Pre-Auth Correlator after username/password
4. **Recommended Actions**: Use ALLOW/BLOCK only (not CHALLENGE)

### maxmind-preauth-config.json

Pre-auth configuration example with recommended settings.

**Key Configuration Differences**:

| Setting | Post-Auth Value | Pre-Auth Value | Reason |
|---------|----------------|----------------|--------|
| mediumRiskAction | CHALLENGE | ALLOW | User not yet identified |
| highRiskAction | BLOCK | BLOCK | Primary security control |
| deviceTrackingEnabled | true | false | Less useful without user correlation |

**Import Instructions**:

1. Create pre-auth flow structure manually in admin console
2. Add MaxMind minFraud execution **before** Username Password Form
3. Add MaxMind Pre-Auth Correlator **after** Username Password Form
4. Configure MaxMind with values from `maxmind-preauth-config.json`
5. Replace account ID and license key with your credentials

### Testing Pre-Auth Mode

#### 1. Verify Pre-Auth Execution

Check logs for pre-auth fraud check:

```bash
docker-compose logs keycloak | grep "pre-authentication fraud check"
```

Expected output:
```
Performing pre-authentication fraud check from IP: 203.0.113.50, session: abc-123-def
```

#### 2. Verify Database Records

```sql
-- Check for pre-auth records
SELECT
    id,
    timestamp,
    is_pre_auth,
    ip_address,
    session_id,
    user_id,
    username,
    risk_score,
    decision
FROM maxmind_minfraud_check
WHERE is_pre_auth = true
ORDER BY timestamp DESC
LIMIT 5;
```

Before login: `user_id IS NULL`
After successful login: `user_id` populated by correlator

#### 3. Verify Correlation

```sql
-- Check correlation is working
SELECT
    timestamp as check_time,
    correlated_at,
    EXTRACT(EPOCH FROM (correlated_at - timestamp)) as delay_seconds,
    username,
    risk_score,
    decision
FROM maxmind_minfraud_check
WHERE is_pre_auth = true
  AND user_id IS NOT NULL
ORDER BY timestamp DESC
LIMIT 5;
```

Expected: `delay_seconds` < 5 seconds (time to enter credentials)

#### 4. Test IP Blocking

1. Add your test IP to `ipBlocklist` in config
2. Try to log in
3. Should be blocked immediately without seeing login form
4. Remove IP from blocklist after testing

## Comparing Modes

| Feature | Post-Auth Mode | Pre-Auth Mode |
|---------|---------------|---------------|
| **Timing** | After username/password | Before username/password |
| **Accuracy** | High (has user email) | Lower (IP-only) |
| **Bot Protection** | Limited | Excellent |
| **CHALLENGE Action** | Recommended | Not recommended |
| **Setup Complexity** | Simple | Requires correlator |
| **Best For** | Normal deployments | High-attack environments |

## Next Steps

- See [SETUP.md](../docs/SETUP.md) for detailed installation instructions
- See [CONFIGURATION.md](../docs/CONFIGURATION.md) for tuning guidance
- See [DATABASE.md](../docs/DATABASE.md) for pre-auth database queries
- See [TROUBLESHOOTING.md](../docs/TROUBLESHOOTING.md) if you encounter issues
