# Configuration Reference

Complete reference for all configuration options in the MaxMind minFraud Keycloak extension.

## Table of Contents

1. [Authenticator Configuration](#authenticator-configuration)
2. [Service Levels](#service-levels)
3. [Risk Thresholds](#risk-thresholds)
4. [Risk Actions](#risk-actions)
5. [Device Tracking](#device-tracking)
6. [Database Recording](#database-recording)
7. [Error Handling](#error-handling)
   - [API Failure Mode](#api-failure-mode)
   - [Timeouts](#connection-timeout-ms)
8. [Event Configuration](#event-configuration)
9. [Advanced Scenarios](#advanced-scenarios)

## Authenticator Configuration

All configuration is done through the Keycloak Admin Console under Authentication → Flows → [Your Flow] → MaxMind minFraud → Config.

### MaxMind Account ID

**Type**: String (numeric)
**Required**: Yes
**Example**: `123456`

Your MaxMind account ID. Found in your MaxMind account portal under "Account Information".

### MaxMind License Key

**Type**: Password (secret)
**Required**: Yes
**Example**: `xK7jR9Lm2pQ5vN8h`

Your MaxMind license key generated for minFraud API access. This is stored encrypted in the Keycloak database.

**Security Note**: Never commit this to version control or share publicly.

### Service Level

**Type**: Dropdown
**Required**: Yes
**Options**: SCORE, INSIGHTS, FACTORS
**Default**: SCORE

Determines which MaxMind minFraud API endpoint to use.

| Service Level | Data Returned | Cost per Query | Use Case |
|--------------|---------------|----------------|----------|
| **SCORE** | Risk score only | ~$0.005 | Basic fraud detection, cost-sensitive deployments |
| **INSIGHTS** | Score + geolocation, anonymizer detection, device tracking | ~$0.01 | Enhanced fraud detection with context |
| **FACTORS** | Everything in Insights + detailed risk factors | ~$0.02 | Maximum fraud prevention, detailed analysis |

**Example Response Data**:

```json
// SCORE
{
  "risk_score": 15.5,
  "id": "abc-123-def"
}

// INSIGHTS (includes SCORE data plus)
{
  "ip_address": {
    "country": "US",
    "is_anonymous": false,
    "is_anonymous_vpn": false
  },
  "device": {
    "confidence": 85,
    "id": "device-xyz"
  }
}

// FACTORS (includes INSIGHTS data plus)
{
  "subscores": {
    "email_address": 2.5,
    "ip_address": 5.0,
    "device": 1.5
  },
  "risk_score_reasons": [...]
}
```

### Enable Device Tracking

**Type**: Boolean
**Required**: No
**Default**: false

Enables MaxMind Device Tracking JavaScript for enhanced device fingerprinting.

**When enabled**:
- MaxMind JavaScript is injected into login page
- Device session ID is collected client-side
- Session ID sent to minFraud API
- MaxMind tracks device across login attempts

**Requirements**:
- Browsers must have JavaScript enabled
- `https://device.maxmind.com` must be accessible
- Works best with INSIGHTS or FACTORS service level

**Recommendation**: Enable for production environments for better fraud detection.

## Service Levels

### Choosing the Right Service Level

#### Use SCORE if:
- Budget is primary concern
- You only need basic risk assessment
- Processing high volume of logins
- Starting out with fraud detection

#### Use INSIGHTS if:
- Need geolocation data for risk decisions
- Want to detect VPNs/proxies/Tor
- Using device tracking
- Need balance of cost and features

#### Use FACTORS if:
- Maximum fraud prevention is critical
- Need detailed forensic analysis
- Compliance requires detailed logging
- Want to fine-tune risk thresholds per factor

### Upgrading Service Levels

You can upgrade from SCORE → INSIGHTS → FACTORS without code changes:

1. Update subscription in MaxMind portal
2. Change "Service Level" in authenticator config
3. Save configuration
4. New fraud checks will use upgraded service level

**Note**: Historical data in database remains from the service level that was active at the time.

## Risk Thresholds

### Low Risk Threshold

**Type**: Integer (0-100)
**Required**: Yes
**Default**: 30

Maximum risk score for "low risk" classification.

- Scores **≤ this value** are considered LOW RISK
- Scores **> this value and ≤ High Risk Threshold** are MEDIUM RISK
- Scores **> High Risk Threshold** are HIGH RISK

**Examples**:
- `20`: Very strict (only scores 0-20 are low risk)
- `30`: Balanced (recommended)
- `40`: Lenient (fewer false positives)

### High Risk Threshold

**Type**: Integer (0-100)
**Required**: Yes
**Default**: 70

Minimum risk score for "high risk" classification.

**Examples**:
- `60`: Very strict (scores 60+ are high risk)
- `70`: Balanced (recommended)
- `80`: Lenient (only very high scores trigger high risk actions)

### Threshold Tuning Guide

Monitor your fraud check data to tune thresholds:

```sql
-- Get percentile distribution
SELECT
  PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY risk_score) as p25,
  PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY risk_score) as median,
  PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY risk_score) as p75,
  PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY risk_score) as p90,
  PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY risk_score) as p95
FROM maxmind_minfraud_check
WHERE realm_id = 'your-realm-id'
  AND timestamp > NOW() - INTERVAL '30 days';
```

**Typical Distributions**:
- Legitimate users: 0-30 (75% of traffic)
- Suspicious but not fraudulent: 30-70 (20% of traffic)
- Likely fraudulent: 70-100 (5% of traffic)

Adjust thresholds based on your user base and risk tolerance.

## Risk Actions

### Low Risk Action

**Type**: Dropdown
**Options**: ALLOW, CHALLENGE, BLOCK
**Default**: ALLOW

Action to take for low risk logins (score ≤ Low Risk Threshold).

**Recommended**: ALLOW

### Medium Risk Action

**Type**: Dropdown
**Options**: ALLOW, CHALLENGE, BLOCK
**Default**: CHALLENGE

Action to take for medium risk logins (Low Threshold < score ≤ High Threshold).

**Recommended**: CHALLENGE (require MFA)

### High Risk Action

**Type**: Dropdown
**Options**: ALLOW, CHALLENGE, BLOCK
**Default**: BLOCK

Action to take for high risk logins (score > High Threshold).

**Recommended**: BLOCK in production, CHALLENGE during testing

### Action Types Explained

#### ALLOW

- Login proceeds normally
- No additional authentication required
- Fraud check result is logged
- User is not notified

**Use for**: Low risk scenarios, logging-only mode

#### CHALLENGE

- Login proceeds but requires additional authentication
- Triggers conditional MFA flows (OTP, WebAuthn, etc.)
- If user doesn't have MFA configured, may be prompted to set it up
- User sees MFA challenge screen

**Use for**: Medium risk scenarios, step-up authentication

**Requirements**:
- OTP or WebAuthn must be configured in authentication flow as CONDITIONAL
- Users should have MFA set up (or will be prompted)

**Example Flow**:
```
1. User enters username/password ✓
2. MaxMind check returns medium risk
3. User prompted for OTP code
4. User enters OTP ✓
5. Login succeeds
```

#### BLOCK

- Login is immediately denied
- User sees error page
- Authentication flow terminates
- Fraud check logged as "BLOCKED"

**Use for**: High risk scenarios, zero-tolerance policy

**User Experience**:
User sees an error page with message:
```
Login attempt blocked due to suspicious activity.
Please contact support if you believe this is an error.
```

### Configuration Examples

#### Strict Security (Zero Trust)

```
Low Risk Threshold: 20
High Risk Threshold: 50
Low Risk Action: ALLOW
Medium Risk Action: CHALLENGE
High Risk Action: BLOCK
```

**Result**: Only very clean logins proceed. Most users will need MFA. High risk blocked.

#### Balanced (Recommended)

```
Low Risk Threshold: 30
High Risk Threshold: 70
Low Risk Action: ALLOW
Medium Risk Action: CHALLENGE
High Risk Action: BLOCK
```

**Result**: Most legitimate users proceed. Suspicious users get MFA. Fraudsters blocked.

#### Lenient (User-Friendly)

```
Low Risk Threshold: 40
High Risk Threshold: 80
Low Risk Action: ALLOW
Medium Risk Action: ALLOW
High Risk Action: CHALLENGE
```

**Result**: Most users proceed without friction. Only very suspicious logins require MFA.

#### Logging Only (Testing)

```
Low Risk Threshold: 30
High Risk Threshold: 70
Low Risk Action: ALLOW
Medium Risk Action: ALLOW
High Risk Action: ALLOW
```

**Result**: All logins proceed. Use this to collect data before enabling enforcement.

## Device Tracking

### How Device Tracking Works

1. MaxMind JavaScript loads on login page
2. Script collects device fingerprint (browser, OS, screen resolution, fonts, etc.)
3. Device session ID generated
4. Session ID sent to Keycloak with login form
5. Keycloak sends session ID to minFraud API
6. MaxMind matches device to historical data
7. Risk score adjusted based on device reputation

### Enabling Device Tracking

1. Set "Enable Device Tracking" to **true**
2. Ensure service level is **INSIGHTS** or **FACTORS** (SCORE doesn't use device data)
3. Verify `https://device.maxmind.com` is accessible from client browsers
4. Test with browser console open to see device session ID

### Browser Console Testing

Open browser console during login to see:

```javascript
MaxMind Device Session ID: abc-123-def-456
```

If you don't see this, device tracking is not working.

### Privacy Considerations

Device tracking collects:
- Browser user agent
- Screen resolution
- Installed fonts
- Timezone
- Language
- Canvas fingerprint

**Compliance**:
- Update privacy policy to mention device fingerprinting
- Consider GDPR implications in EU
- Provide opt-out mechanism if required

## Database Recording

### Record Fraud Checks to Database

**Type**: Boolean
**Required**: No
**Default**: true

Controls whether fraud check results are stored in the `maxmind_minfraud_check` database table.

#### When Enabled (Default)

- All fraud check results are stored in the database
- Enables long-term analytics and reporting
- Allows querying historical fraud data across any time period
- Database table grows over time (plan for retention policies)
- Fraud patterns can be analyzed across users and time
- Useful for compliance and audit requirements

#### When Disabled

- Fraud checks are performed but not stored in database
- Results still logged to Keycloak events (if event logging enabled)
- Reduces database storage requirements
- No long-term fraud check history available
- Useful for testing environments or privacy-sensitive deployments
- Cannot query fraud check history beyond event retention period

#### Events vs Database Comparison

| Feature | Keycloak Events | Database Table |
|---------|----------------|----------------|
| **Retention** | Days to weeks (configurable) | Months to years (unlimited) |
| **Query Complexity** | Limited | Full SQL queries |
| **Performance** | Fast for recent data | Optimized with indexes |
| **Analytics** | Basic | Advanced (joins, aggregations) |
| **Storage Impact** | Part of event table | Separate dedicated table |

#### Configuration Example

```yaml
# Enable database recording (default)
recordFraudChecks: true

# Disable database recording (events only)
recordFraudChecks: false
```

#### When to Disable

Consider disabling database recording when:
- **Testing environments**: Avoid accumulating test data
- **Privacy requirements**: Minimize data retention
- **Storage constraints**: Limited database capacity
- **Event-only logging**: Events provide sufficient audit trail

#### Important Notes

- **Event logging is independent**: Fraud checks are always logged to Keycloak events (if event logging is enabled), regardless of this setting
- **Backward compatibility**: Defaults to `true` to maintain existing behavior
- **Runtime changes**: Can be changed without restarting Keycloak (applies to new logins)
- **No data loss**: Historical data remains if you disable recording; only new checks are affected

#### Recommendation

**Production**: Keep enabled for:
- Compliance and audit requirements
- Fraud pattern analysis
- Long-term reporting
- Incident investigation

**Development/Testing**: Consider disabling to:
- Avoid test data accumulation
- Reduce database cleanup effort
- Simplify environment management

## Error Handling

### API Failure Mode

**Type**: Dropdown
**Options**: FAIL_OPEN, FAIL_CLOSED
**Default**: FAIL_OPEN

Determines behavior when MaxMind API call fails.

#### FAIL_OPEN

- API error → login proceeds
- Error is logged
- Fraud check stored with error message
- Decision set to "ERROR"

**Use for**:
- Development environments
- Testing
- When user experience is priority
- Non-critical applications

**Risks**:
- Fraudsters could exploit API downtime
- False sense of security if API silently fails

#### FAIL_CLOSED

- API error → login blocked
- User sees error page
- Error is logged
- Fraud check stored with error message

**Use for**:
- Production environments
- High-security applications
- When security is priority over availability
- Compliance requirements

**Risks**:
- Legitimate users blocked during API outages
- Higher support burden
- Potential revenue loss if login is critical

### Connection Timeout (ms)

**Type**: Integer (milliseconds)
**Required**: No
**Default**: 3000

Maximum time to wait for connection establishment to MaxMind API.

**Recommended Values**:
- **1000-2000ms**: Fast networks, aggressive timeout
- **3000ms**: Balanced (default)
- **5000-10000ms**: Slower networks, more tolerance

### Read Timeout (ms)

**Type**: Integer (milliseconds)
**Required**: No
**Default**: 5000

Maximum time to wait for MaxMind API response after connection is established.

**Recommended Values**:
- **2000-3000ms**: Fast response required
- **5000ms**: Balanced (default)
- **10000ms**: High tolerance for slow responses

### Timeout Behavior

**Total worst-case delay**: Connection Timeout + Read Timeout (default: 8 seconds)

When a timeout occurs:
1. API call is aborted
2. Error logged: "API error: Read timed out" or "API error: Connection timed out"
3. Fraud check stored with `error_message` set
4. Configured **Fail Mode** determines whether to ALLOW or BLOCK login

**Example**:
```
Connection Timeout: 3000ms
Read Timeout: 5000ms
Fail Mode: FAIL_OPEN

Result: If MaxMind doesn't respond within 8 seconds, login proceeds
```

**Tuning Recommendations**:
- Monitor `error_message` column for timeout frequency
- If timeouts are rare (<1%), keep defaults
- If timeouts are common (>5%), increase timeouts or check network connectivity
- Balance security (longer timeout = more fraud detection) vs UX (shorter timeout = faster login)

### API Failure Scenarios

Common failure reasons:

1. **Network timeout**: Keycloak can't reach MaxMind API
2. **Invalid credentials**: Account ID or License Key incorrect
3. **Insufficient credits**: MaxMind account out of credits
4. **Rate limit**: Too many requests
5. **IP blocked**: Keycloak IP not in allowlist

All failures are logged in `error_message` column:

```sql
SELECT timestamp, ip_address, error_message
FROM maxmind_minfraud_check
WHERE error_message IS NOT NULL
ORDER BY timestamp DESC;
```

## Event Configuration

The MaxMind minFraud authenticator automatically logs all fraud checks to Keycloak's event system in addition to the database. Events provide real-time audit trails and are useful for compliance, monitoring, and integration with SIEM systems.

### Enabling User Events

By default, Keycloak does not save user events. To enable event logging:

1. Navigate to **Realm Settings** → **Events** → **User Events Settings**
2. Enable **Save Events**
3. Set **Expiration** (e.g., 7 days, 30 days, or 90 days)
4. Optionally configure **Event Listeners** for external integrations

**Recommended Settings**:
```
Save Events: ON
Expiration: 30 days
Event Listeners: jboss-logging, email (optional)
```

### Event Types

All fraud checks generate user events with type `LOGIN` or `LOGIN_ERROR` depending on the outcome. The events include custom detail keys prefixed with `maxmind_minfraud_`.

### Event Detail Keys

All fraud check events include the following custom detail keys:

| Detail Key | Description | Example Value | When Present |
|-----------|-------------|---------------|--------------|
| `maxmind_minfraud_risk_score` | Risk score from MaxMind (0-100) | `45.50` | Success only |
| `maxmind_minfraud_risk_level` | Evaluated risk level | `LOW`, `MEDIUM`, `HIGH` | Success only |
| `maxmind_minfraud_decision` | Action taken or error | `ALLOW`, `CHALLENGE`, `BLOCK`, `ERROR` | Always |
| `maxmind_minfraud_request_id` | MaxMind API request ID for correlation | `abc123-def456-...` | Success only |
| `maxmind_minfraud_service_level` | Service level used | `SCORE`, `INSIGHTS`, `FACTORS` | Always |
| `maxmind_minfraud_action` | Specific action detail | `CHALLENGE`, `BLOCK` | Action taken only |
| `maxmind_minfraud_error` | Error message | `API error: timeout` | Error only |
| `auth_method` | Authentication method identifier | `maxmind_minfraud` | Always |

### Viewing Events in Admin Console

1. Navigate to **Realm Settings** → **Events** → **User Events**
2. Filter events by:
   - **User**: Select specific user to see their fraud checks
   - **Type**: Filter by LOGIN, LOGIN_ERROR
   - **Date Range**: Last hour, day, week, or custom range
3. Click on any event to expand and view all details

### Event Storage vs Database Storage

The extension uses a dual logging strategy:

| Storage | Purpose | Retention | Query Method | Use Case |
|---------|---------|-----------|--------------|----------|
| **Events** (`event_entity` table) | Short-term audit | Days to weeks | Admin Console or SQL | Compliance, real-time monitoring |
| **Database** (`maxmind_minfraud_check` table) | Long-term analytics | Months to years | SQL queries | Fraud analysis, reporting, ML training |

**Recommendation**: Use events for compliance and real-time monitoring (7-30 days), use database table for historical analysis and reporting.

### Event Listeners

Event listeners allow real-time integration with external systems.

#### Built-in Listeners

**jboss-logging** (recommended):
- Logs all events to Keycloak server logs
- Useful for debugging and centralized logging
- No additional configuration required

**email** (optional):
- Send email notifications for specific events
- Requires SMTP configuration in Realm Settings
- Can be noisy for high-volume applications

#### Custom Event Listeners

You can create custom event listeners to:
- Send fraud check events to SIEM (Splunk, ELK, etc.)
- Trigger webhooks for high-risk logins
- Update external fraud databases
- Send alerts to security teams

**Example: Filtering MaxMind Events**

```java
@Override
public void onEvent(Event event) {
    if (event.getDetails() != null &&
        event.getDetails().containsKey("maxmind_minfraud_decision")) {
        String decision = event.getDetails().get("maxmind_minfraud_decision");
        if ("BLOCK".equals(decision) || "ERROR".equals(decision)) {
            // Send alert to security team
            alertSecurityTeam(event);
        }
    }
}
```

See [Keycloak Event Listener SPI documentation](https://www.keycloak.org/docs/latest/server_development/#_events) for implementation details.

### Querying Events from Database

Events are stored in the `event_entity` table. The `details` column contains JSON-encoded event details.

#### Example Queries

**View all fraud check events for a user**:
```sql
SELECT
    time,
    type,
    user_id,
    details
FROM event_entity
WHERE user_id = 'user-uuid-here'
  AND details LIKE '%maxmind_minfraud%'
ORDER BY time DESC
LIMIT 20;
```

**Find high-risk login attempts**:
```sql
SELECT
    time,
    user_id,
    ip_address,
    details
FROM event_entity
WHERE realm_id = 'realm-id-here'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000
  AND details LIKE '%maxmind_minfraud_risk_level=HIGH%'
ORDER BY time DESC;
```

**Find blocked login attempts**:
```sql
SELECT
    time,
    user_id,
    details
FROM event_entity
WHERE realm_id = 'realm-id-here'
  AND details LIKE '%maxmind_minfraud_decision=BLOCK%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY time DESC;
```

**Find API errors**:
```sql
SELECT
    time,
    user_id,
    details
FROM event_entity
WHERE realm_id = 'realm-id-here'
  AND details LIKE '%maxmind_minfraud_error%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 day') * 1000
ORDER BY time DESC;
```

**Correlate events with fraud check records**:
```sql
SELECT
    e.time as event_time,
    e.user_id,
    e.details as event_details,
    f.risk_score,
    f.decision,
    f.timestamp as fraud_check_time,
    f.raw_response
FROM event_entity e
LEFT JOIN maxmind_minfraud_check f
    ON e.user_id = f.user_id
    AND ABS(EXTRACT(EPOCH FROM f.timestamp) * 1000 - e.time) < 5000  -- Within 5 seconds
WHERE e.details LIKE '%maxmind_minfraud%'
  AND e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY e.time DESC
LIMIT 100;
```

**Note**: Event timestamps in `event_entity.time` are stored as Unix epoch in **milliseconds**, while `maxmind_minfraud_check.timestamp` is a standard PostgreSQL timestamp.

### Event Expiration

Configure event expiration based on your compliance requirements:

| Retention Period | Use Case |
|-----------------|----------|
| **1-7 days** | Development, testing, minimal compliance |
| **30 days** | Standard compliance (PCI-DSS recommends 90 days for audit logs) |
| **90 days** | Enhanced compliance, fraud investigation |
| **365+ days** | Not recommended for events - use database table instead |

**Warning**: Long event retention increases database size. For long-term storage, rely on the `maxmind_minfraud_check` table instead of events.

### Event Best Practices

1. **Enable Events for Compliance**: If you need audit trails, enable Save Events
2. **Set Appropriate Expiration**: Balance compliance needs with database size
3. **Use Event Listeners for Real-Time Alerts**: Configure listeners for high-risk events
4. **Query Events for Recent Activity**: Use admin console for last 7-30 days
5. **Query Database for Historical Analysis**: Use SQL queries on `maxmind_minfraud_check` for older data
6. **Monitor Event Storage**: Check database size if using long retention periods
7. **Export Events if Needed**: Use event listeners or scheduled jobs to export events to external systems

### Troubleshooting Events

If events are not appearing:

1. **Verify Save Events is enabled**: Realm Settings → Events → User Events Settings → Save Events = ON
2. **Check expiration settings**: Events older than expiration are automatically deleted
3. **Verify fraud check is executing**: Check `maxmind_minfraud_check` table for corresponding records
4. **Check server logs**: Look for errors in Keycloak logs related to event storage
5. **Verify database permissions**: Keycloak needs INSERT permission on `event_entity` table

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md#event-viewing-issues) for more details.

## Advanced Scenarios

### Multi-Realm Configuration

Each realm can have different MaxMind configurations:

**Realm: Corporate**
```
Service Level: FACTORS
Device Tracking: true
High Risk Threshold: 60
High Risk Action: BLOCK
Fail Mode: FAIL_CLOSED
```

**Realm: Customer**
```
Service Level: SCORE
Device Tracking: false
High Risk Threshold: 80
High Risk Action: CHALLENGE
Fail Mode: FAIL_OPEN
```

### Conditional Flows

Use MaxMind authenticator as part of conditional flow:

```
Browser Flow
└── Browser Forms (ALTERNATIVE)
    ├── Username Password Form (REQUIRED)
    └── Conditional OTP (CONDITIONAL)
        ├── Condition - User Configured (REQUIRED)
        └── MaxMind minFraud (REQUIRED)
        └── OTP Form (REQUIRED)
```

MaxMind only executes if user has OTP configured.

### Testing with Different MaxMind Accounts

Use separate MaxMind accounts for dev/staging/prod:

**Development**
- Sandbox account (if available)
- Lower query limits
- Test data

**Production**
- Production account
- Higher limits
- Real fraud detection

### Monitoring Configuration

Set up alerts for:

```sql
-- High blocked rate
SELECT COUNT(*) as blocked_count
FROM maxmind_minfraud_check
WHERE decision = 'BLOCKED'
  AND timestamp > NOW() - INTERVAL '1 hour';
-- Alert if > 10

-- API errors
SELECT COUNT(*) as error_count
FROM maxmind_minfraud_check
WHERE error_message IS NOT NULL
  AND timestamp > NOW() - INTERVAL '15 minutes';
-- Alert if > 5
```

## Configuration Best Practices

1. **Start conservative**: Use FAIL_OPEN and ALLOW actions initially
2. **Collect data**: Run in logging-only mode for 1-2 weeks
3. **Analyze patterns**: Review risk score distribution
4. **Tune thresholds**: Adjust based on your data
5. **Enable enforcement**: Gradually enable CHALLENGE and BLOCK actions
6. **Monitor closely**: Watch for false positives
7. **Iterate**: Continuously refine based on feedback

## Next Steps

- See [DATABASE.md](DATABASE.md) for querying fraud data
- See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues
- See [API.md](API.md) for MaxMind API details
