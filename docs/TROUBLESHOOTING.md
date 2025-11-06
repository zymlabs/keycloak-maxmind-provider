# Troubleshooting Guide

Common issues and solutions for the Keycloak MaxMind minFraud extension.

## Table of Contents

1. [Installation Issues](#installation-issues)
2. [Configuration Issues](#configuration-issues)
3. [MaxMind API Issues](#maxmind-api-issues)
4. [Device Tracking Issues](#device-tracking-issues)
5. [Performance Issues](#performance-issues)
6. [False Positives/Negatives](#false-positivesnegatives)
7. [Event Viewing Issues](#event-viewing-issues)

## Installation Issues

### Extension Not Appearing in Admin Console

**Symptom**: MaxMind minFraud authenticator doesn't appear in the authenticator dropdown.

**Possible Causes**:
1. JAR not deployed correctly
2. Keycloak not restarted after deployment
3. SPI registration files missing/incorrect

**Solutions**:

```bash
# 1. Verify JAR location
ls -l /path/to/keycloak/providers/zymlabs-maxmind-provider.jar

# 2. Check Keycloak logs for deployment errors
tail -f /path/to/keycloak/data/log/keycloak.log | grep -i maxmind

# 3. Rebuild Keycloak (Quarkus distribution)
/path/to/keycloak/bin/kc.sh build

# 4. Restart Keycloak
/path/to/keycloak/bin/kc.sh stop
/path/to/keycloak/bin/kc.sh start
```

**Verify SPI Files**:
```bash
cd /path/to/keycloak-maxmind
jar tf target/zymlabs-maxmind-provider.jar | grep META-INF/services
```

Should show:
```
META-INF/services/org.keycloak.authentication.AuthenticatorFactory
META-INF/services/org.keycloak.provider.Spi
```

### Database Table Not Created

**Symptom**: `maxmind_minfraud_check` table doesn't exist.

**Check Liquibase Logs**:

```bash
grep -i liquibase /path/to/keycloak/data/log/keycloak.log
grep -i "maxmind-minfraud" /path/to/keycloak/data/log/keycloak.log
```

**Manual Table Creation**:

If Liquibase fails, create table manually:

```sql
CREATE TABLE maxmind_minfraud_check (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    realm_id VARCHAR(36) NOT NULL,
    username VARCHAR(255),
    email VARCHAR(255),
    ip_address VARCHAR(45) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    risk_score DOUBLE PRECISION NOT NULL,
    decision VARCHAR(20) NOT NULL,
    device_session_id VARCHAR(255),
    request_id VARCHAR(36),
    raw_response TEXT,
    service_level VARCHAR(20),
    error_message TEXT
);

CREATE INDEX idx_user_id ON maxmind_minfraud_check(user_id);
CREATE INDEX idx_realm_id ON maxmind_minfraud_check(realm_id);
CREATE INDEX idx_timestamp ON maxmind_minfraud_check(timestamp);
CREATE INDEX idx_user_timestamp ON maxmind_minfraud_check(user_id, timestamp);
```

### Keycloak Fails to Start

**Symptom**: Keycloak won't start after deploying extension.

**Check for Dependency Conflicts**:

```bash
# Extract JAR and check dependencies
unzip -l target/zymlabs-maxmind-provider.jar

# Look for MANIFEST.MF
unzip -p target/zymlabs-maxmind-provider.jar META-INF/MANIFEST.MF
```

**Check Logs for Errors**:

```bash
grep -i "error\|exception" /path/to/keycloak/data/log/keycloak.log | tail -50
```

**Common Errors**:
- ClassNotFoundException: Missing dependency
- NoSuchMethodError: Keycloak version mismatch
- SQLSyntaxErrorException: Database schema issue

## Configuration Issues

### "MaxMind authenticator is not configured" Error

**Symptom**: Login fails with error "MaxMind authenticator is not configured".

**Cause**: Authenticator config not set up.

**Solution**:

1. Go to Authentication → Flows → [Your Flow]
2. Click Actions (⚙️) next to MaxMind minFraud execution
3. Click **Config**
4. Fill in all required fields:
   - Account ID
   - License Key
   - Service Level
   - Risk thresholds
   - Risk actions
5. Click **Save**

### Invalid Account ID or License Key

**Symptom**: Logs show "invalid account ID" or "permission required" errors.

**Verify Credentials**:

1. Log into MaxMind account portal
2. Verify Account ID under Account Information
3. Re-generate license key if needed
4. Ensure license key type is `maxmindfr-minfraud-user-token`

**Test API Access**:

```bash
# Test with curl
curl -u "{ACCOUNT_ID}:{LICENSE_KEY}" \
  "https://minfraud.maxmind.com/minfraud/v2.0/score" \
  -H "Content-Type: application/json" \
  -d '{
    "device": {
      "ip_address": "8.8.8.8"
    }
  }'
```

Should return JSON with risk_score.

### Configuration Not Taking Effect

**Symptom**: Changes to authenticator config don't apply.

**Solutions**:

1. **Clear Keycloak cache**:
```bash
# Restart Keycloak
bin/kc.sh restart
```

2. **Verify config in database**:
```sql
SELECT * FROM authenticator_config
WHERE alias LIKE '%maxmind%';
```

3. **Check realm is correct**:
- Ensure you're configuring the right realm
- Switch realms in admin console dropdown

## MaxMind API Issues

### "API error: Connection timeout"

**Symptom**: Fraud checks fail with connection timeout error.

**Causes**:
1. Keycloak server can't reach internet
2. Firewall blocking outbound HTTPS
3. MaxMind API down (rare)

**Solutions**:

```bash
# 1. Test connectivity from Keycloak server
curl -I https://minfraud.maxmind.com

# 2. Check DNS resolution
nslookup minfraud.maxmind.com

# 3. Test HTTPS connection
openssl s_client -connect minfraud.maxmind.com:443

# 4. Check firewall rules
iptables -L -n | grep -i output
```

**Proxy Configuration**:

If Keycloak is behind a proxy, configure Java system properties:

```bash
# In kc.sh or standalone.conf
export JAVA_OPTS="-Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080"
```

### "Insufficient funds" Error

**Symptom**: API returns "insufficient funds" error.

**Cause**: MaxMind account out of credits.

**Solution**:

1. Log into MaxMind account portal
2. Add credits or update payment method
3. Check current balance and usage

### "Your IP address is not allowed" Error

**Symptom**: API returns "permission required" with IP restriction message.

**Cause**: Keycloak server IP not in MaxMind IP allowlist.

**Solution**:

1. Find Keycloak server's public IP:
```bash
curl ifconfig.me
```

2. In MaxMind portal:
   - Navigate to Account → Security
   - Add IP to allowlist
   - Wait 5 minutes for changes to propagate

### High API Latency

**Symptom**: Logins take 5+ seconds.

**Check API Response Time**:

```sql
-- If you have timing data
SELECT AVG(response_time) FROM maxmind_minfraud_check
WHERE timestamp > NOW() - INTERVAL '1 hour';
```

**Solutions**:

1. **Check MaxMind status**: https://status.maxmind.com
2. **Use lower service level**: SCORE is faster than INSIGHTS/FACTORS
3. **Implement caching** (future enhancement)
4. **Check network latency**:
```bash
ping minfraud.maxmind.com
traceroute minfraud.maxmind.com
```

## Device Tracking Issues

### Device Session ID Not Collected

**Symptom**: `device_session_id` is always NULL in database.

**Check Browser Console**:

1. Open browser DevTools (F12)
2. Go to Console tab
3. Log in
4. Look for "MaxMind Device Session ID"

**Common Issues**:

**1. JavaScript Not Loading**:
```javascript
// Error in console:
// Failed to load resource: https://device.maxmind.com/js/device.js
```
**Solution**: Check if `device.maxmind.com` is accessible from client browser.

**2. Content Security Policy (CSP) Blocking**:
```javascript
// Error in console:
// Refused to load script from 'https://device.maxmind.com' because it violates CSP
```
**Solution**: Update Keycloak CSP headers to allow MaxMind:
```
Content-Security-Policy: script-src 'self' https://device.maxmind.com
```

**3. Device Tracking Not Enabled**:
Check authenticator config:
```sql
SELECT config FROM authenticator_config
WHERE alias LIKE '%maxmind%';
```
Look for `"deviceTrackingEnabled":"true"` in config JSON.

### Device Tracking Script Not Injected

**Symptom**: No MaxMind script in page source.

**Debug**:

1. View page source during login
2. Look for `https://device.maxmind.com/js/device.js`
3. If missing, check authenticator code

**Solution**:

Verify `presentDeviceTrackingForm()` is being called:

```bash
# Check logs
grep -i "device tracking" /path/to/keycloak/data/log/keycloak.log
```

## Performance Issues

### Slow Login Performance

**Symptom**: Login takes 2-5 seconds longer than before.

**Measure Impact**:

```sql
-- Average fraud check for recent logins
SELECT
    AVG(EXTRACT(EPOCH FROM (timestamp - LAG(timestamp) OVER (ORDER BY timestamp)))) as avg_gap_seconds
FROM maxmind_minfraud_check
WHERE timestamp > NOW() - INTERVAL '1 hour';
```

**Optimization Options**:

1. **Use SCORE service level**: Fastest, cheapest
2. **Disable device tracking**: Saves client-side processing
3. **Implement async checking** (requires code modification)
4. **Use FAIL_OPEN**: Doesn't wait for API on errors

### High Database Load

**Symptom**: Database CPU/IO high after enabling extension.

**Check Table Size**:

```sql
SELECT
    pg_size_pretty(pg_total_relation_size('maxmind_minfraud_check')) as total_size,
    pg_size_pretty(pg_relation_size('maxmind_minfraud_check')) as table_size,
    pg_size_pretty(pg_total_relation_size('maxmind_minfraud_check') - pg_relation_size('maxmind_minfraud_check')) as index_size;
```

**Solutions**:

1. **Archive old records**: See [DATABASE.md](DATABASE.md)
2. **Add indexes** for your common queries
3. **Partition table** by month
4. **Reduce raw_response storage**: Modify code to store only summary

## False Positives/Negatives

### Legitimate Users Being Blocked

**Symptom**: Real users can't log in, get "Login too risky" error.

**Investigate**:

```sql
-- Check recent blocks
SELECT username, email, ip_address, risk_score, raw_response
FROM maxmind_minfraud_check
WHERE decision = 'BLOCKED'
  AND timestamp > NOW() - INTERVAL '24 hours'
ORDER BY timestamp DESC;
```

**Common Causes**:

1. **VPN/Proxy Users**: Legitimate users on VPNs get flagged
2. **Corporate Networks**: Shared IP addresses with poor reputation
3. **Thresholds Too Strict**: High Risk Threshold set too low
4. **New Accounts**: New email addresses flagged as suspicious

**Solutions**:

1. **Adjust thresholds**:
   - Increase High Risk Threshold (70 → 80)
   - Change High Risk Action (BLOCK → CHALLENGE)

2. **Whitelist known IPs**:
```sql
-- Check if IP should be whitelisted
SELECT ip_address, COUNT(*), AVG(risk_score)
FROM maxmind_minfraud_check
WHERE decision = 'BLOCKED'
GROUP BY ip_address
ORDER BY COUNT(*) DESC;
```

3. **Review with INSIGHTS/FACTORS**:
   - Upgrade service level for better detection
   - Review `raw_response` for false positive reasons

### Fraudsters Getting Through

**Symptom**: Known fraud attempts not being blocked.

**Investigate**:

```sql
-- Check low-scored logins that might be fraud
SELECT username, email, ip_address, risk_score, decision
FROM maxmind_minfraud_check
WHERE risk_score < 30
  AND timestamp > NOW() - INTERVAL '7 days'
ORDER BY timestamp DESC;
```

**Solutions**:

1. **Lower thresholds**: Decrease Low Risk Threshold (30 → 20)
2. **Upgrade service level**: Use INSIGHTS or FACTORS
3. **Enable device tracking**: Better device reputation
4. **Use CHALLENGE for medium risk**: Force MFA more aggressively

## Event Viewing Issues

The extension logs all fraud checks to both the database (`maxmind_minfraud_check` table) and Keycloak's event system. If you're having trouble viewing events, use this section to diagnose.

### Events Not Appearing in Admin Console

**Symptom**: No fraud check events visible in Realm Settings → Events → User Events.

**Possible Causes**:
1. Save Events not enabled
2. Events expired
3. Wrong realm selected
4. Events not being created

**Solutions**:

**1. Verify Save Events is Enabled**:
```
Navigate to: Realm Settings → Events → User Events Settings
Check: "Save Events" toggle is ON
```

**2. Check Event Expiration**:
```
Navigate to: Realm Settings → Events → User Events Settings
Check: "Expiration" setting
```
If expiration is set to "1 day" and you're looking at older events, they've been deleted.

**3. Verify Fraud Check is Executing**:
```sql
-- Check if fraud checks are being recorded in database
SELECT COUNT(*) FROM maxmind_minfraud_check
WHERE timestamp > NOW() - INTERVAL '1 hour';
```
If this returns > 0 but events are not visible, the issue is with event storage, not fraud detection.

**4. Check Event Storage Directly**:
```sql
-- Query event_entity table directly
SELECT COUNT(*) FROM event_entity
WHERE details LIKE '%maxmind_minfraud%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 hour') * 1000;
```
If this returns 0, events are not being created.

**5. Verify Database Permissions**:
```sql
-- Check if Keycloak can insert events
GRANT INSERT ON event_entity TO keycloak_user;
```

### Events Missing Event Details

**Symptom**: Events appear but don't contain `maxmind_minfraud_*` details.

**Check Event Details**:
```sql
SELECT details FROM event_entity
WHERE time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 hour') * 1000
LIMIT 1;
```

**Expected Output**:
```
maxmind_minfraud_risk_score=45.50,maxmind_minfraud_risk_level=MEDIUM,maxmind_minfraud_decision=CHALLENGE,...
```

**If details are empty or missing fraud data**:
1. Extension may not be latest version
2. Event creation code may have been removed
3. Check Keycloak logs for errors during fraud check

### Events Not Correlating with Database Records

**Symptom**: Events and database records don't match up.

**Verify Time Synchronization**:

```sql
-- Compare event and database record counts
SELECT
    (SELECT COUNT(*) FROM event_entity
     WHERE details LIKE '%maxmind_minfraud%'
       AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 day') * 1000) as event_count,
    (SELECT COUNT(*) FROM maxmind_minfraud_check
     WHERE timestamp > NOW() - INTERVAL '1 day') as db_count;
```

**Expected**: Counts should be equal or very close.

**If counts differ significantly**:
1. Events may have expired (check expiration setting)
2. Event storage may have failed for some records
3. Check Keycloak logs for event storage errors

**Correlate by Request ID**:
```sql
-- Find matching records
SELECT
    e.time,
    e.details,
    f.timestamp,
    f.risk_score,
    f.decision
FROM event_entity e
LEFT JOIN maxmind_minfraud_check f
    ON e.user_id = f.user_id
    AND e.details LIKE '%' || f.request_id || '%'
WHERE e.details LIKE '%maxmind_minfraud%'
  AND e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 day') * 1000
LIMIT 10;
```

### Events Showing Wrong Event Type

**Symptom**: Events have wrong type (not LOGIN or LOGIN_ERROR).

**Check Event Types**:
```sql
SELECT type, COUNT(*) FROM event_entity
WHERE details LIKE '%maxmind_minfraud%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 day') * 1000
GROUP BY type;
```

**Expected Types**:
- `LOGIN`: Successful logins (including ALLOW and CHALLENGE actions)
- `LOGIN_ERROR`: Failed logins (BLOCK actions and API errors)

**Important**: CHALLENGE actions generate `LOGIN` events (not LOGIN_ERROR) because:
- CHALLENGE uses `context.success()` to allow authentication to continue
- The authenticator sets auth session notes (`maxmind_challenge=true`, `maxmind_risk_score=<score>`)
- Conditional authenticators should check these notes to require MFA
- After MFA succeeds, the final event type is `LOGIN`

**If types are incorrect**: This is expected Keycloak behavior - the event type is determined by the authentication flow outcome, not the fraud check result.

**Querying for CHALLENGE Events**:
```sql
SELECT time, user_id, ip_address, details
FROM event_entity
WHERE details LIKE '%maxmind_minfraud_action=CHALLENGE%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY time DESC;
```

### Finding Events by Risk Level

**Query for High-Risk Events**:
```sql
SELECT time, user_id, ip_address, details
FROM event_entity
WHERE details LIKE '%maxmind_minfraud_risk_level=HIGH%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY time DESC;
```

**Query for Blocked Logins**:
```sql
SELECT time, user_id, details
FROM event_entity
WHERE details LIKE '%maxmind_minfraud_decision=BLOCK%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY time DESC;
```

**Query for API Errors**:
```sql
SELECT time, user_id, details
FROM event_entity
WHERE details LIKE '%maxmind_minfraud_error%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 day') * 1000
ORDER BY time DESC;
```

### Exporting Events for External Analysis

**Export to CSV**:
```sql
COPY (
    SELECT
        to_timestamp(time / 1000) as timestamp,
        user_id,
        ip_address,
        details
    FROM event_entity
    WHERE details LIKE '%maxmind_minfraud%'
      AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '30 days') * 1000
    ORDER BY time DESC
) TO '/tmp/maxmind_events.csv' WITH CSV HEADER;
```

**Export to JSON**:
```sql
COPY (
    SELECT json_build_object(
        'timestamp', to_timestamp(time / 1000),
        'user_id', user_id,
        'ip_address', ip_address,
        'details', details
    )
    FROM event_entity
    WHERE details LIKE '%maxmind_minfraud%'
      AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '30 days') * 1000
    ORDER BY time DESC
) TO '/tmp/maxmind_events.json';
```

### Event Storage Performance

**Symptom**: Slow logins when Save Events is enabled.

**Check Event Table Size**:
```sql
SELECT
    pg_size_pretty(pg_total_relation_size('event_entity')) as total_size,
    (SELECT COUNT(*) FROM event_entity) as row_count;
```

**If table is very large (>1GB or >1M rows)**:
1. Reduce event expiration (30 days → 7 days)
2. Set up event archiving to external system
3. Consider disabling Save Events and relying on database table only

**Optimize Event Queries**:
```sql
-- Create index for faster fraud event queries
CREATE INDEX idx_event_entity_details_maxmind
ON event_entity USING btree (time)
WHERE details LIKE '%maxmind_minfraud%';
```

### Common Event Detail Keys Reference

For quick troubleshooting, here are all possible event detail keys:

| Key | Present When | Example Value |
|-----|--------------|---------------|
| `maxmind_minfraud_risk_score` | Success | `45.50` |
| `maxmind_minfraud_risk_level` | Success | `LOW`, `MEDIUM`, `HIGH` |
| `maxmind_minfraud_decision` | Always | `ALLOW`, `CHALLENGE`, `BLOCK`, `ERROR` |
| `maxmind_minfraud_request_id` | Success | `abc123-def456-...` |
| `maxmind_minfraud_service_level` | Always | `SCORE`, `INSIGHTS`, `FACTORS` |
| `maxmind_minfraud_action` | CHALLENGE/BLOCK | `CHALLENGE`, `BLOCK` |
| `maxmind_minfraud_error` | Error | `API error: timeout` |
| `auth_method` | Always | `maxmind_minfraud` |

## Getting More Help

### Enable Debug Logging

Add to Keycloak logging config:

```bash
# In standalone.xml or quarkus.properties
logger.maxmind.level=DEBUG
logger.maxmind.name=com.zymlabs.keycloak.maxmind_provider
```

Restart Keycloak and check logs:

```bash
tail -f /path/to/keycloak/data/log/keycloak.log | grep -i maxmind
```

### Collect Diagnostic Information

When reporting issues, include:

1. **Keycloak version**: `bin/kc.sh --version`
2. **Extension version**: Check pom.xml
3. **Relevant logs**:
```bash
grep -A 20 -B 5 "maxmind\|fraud" /path/to/keycloak/data/log/keycloak.log > debug.log
```

4. **Database record** (redact sensitive info):
```sql
SELECT id, timestamp, risk_score, decision, error_message, service_level
FROM maxmind_minfraud_check
WHERE id = <failing_id>;
```

5. **Configuration** (redact license key):
```sql
SELECT alias, config FROM authenticator_config
WHERE alias LIKE '%maxmind%';
```

### Contact Support

- **GitHub Issues**: https://github.com/your-org/keycloak-maxmind/issues
- **MaxMind Support**: https://support.maxmind.com (for API issues)
- **Keycloak Community**: https://keycloak.discourse.group (for Keycloak issues)

## Next Steps

- Review [CONFIGURATION.md](CONFIGURATION.md) for tuning options
- Check [DATABASE.md](DATABASE.md) for analytics to identify patterns
- See [API.md](API.md) for MaxMind API details
