# Example Configurations

This directory contains example configurations for the MaxMind minFraud Keycloak extension.

## Files

### browser-flow-with-maxmind.json

Example authentication flow that includes MaxMind fraud detection.

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

## Next Steps

- See [SETUP.md](../docs/SETUP.md) for detailed installation instructions
- See [CONFIGURATION.md](../docs/CONFIGURATION.md) for tuning guidance
- See [TROUBLESHOOTING.md](../docs/TROUBLESHOOTING.md) if you encounter issues
