# Database Schema and Analytics

Complete reference for the MaxMind fraud check database schema and analytics queries.

**Note**: Database recording is optional and controlled by the **Record Fraud Checks to Database** configuration option (enabled by default). If disabled, fraud checks are still performed and logged to Keycloak events, but not stored in the database table for long-term analytics.

## Table of Contents

1. [Database Schema](#database-schema)
2. [Common Queries](#common-queries)
3. [Analytics Queries](#analytics-queries)
4. [Event Integration](#event-integration)
5. [Data Retention](#data-retention)
6. [Performance Optimization](#performance-optimization)

## Database Schema

### Table: maxmind_minfraud_check

Stores all fraud check results for auditing and analytics.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGINT | NO | Primary key, auto-increment |
| `user_id` | VARCHAR(36) | NO | Keycloak user ID (UUID) |
| `realm_id` | VARCHAR(36) | NO | Keycloak realm ID (UUID) |
| `username` | VARCHAR(255) | YES | Username at time of check |
| `email` | VARCHAR(255) | YES | Email at time of check |
| `ip_address` | VARCHAR(45) | NO | User's IP address (IPv4 or IPv6) |
| `timestamp` | TIMESTAMP | NO | When fraud check occurred |
| `risk_score` | DOUBLE | NO | MaxMind risk score (0-100, or -1 for errors) |
| `decision` | VARCHAR(20) | NO | ALLOWED, CHALLENGED, BLOCKED, or ERROR |
| `device_session_id` | VARCHAR(255) | YES | MaxMind device tracking session ID |
| `request_id` | VARCHAR(36) | YES | MaxMind request ID for correlation |
| `raw_response` | TEXT | YES | Full JSON response from MaxMind API |
| `service_level` | VARCHAR(20) | YES | SCORE, INSIGHTS, or FACTORS |
| `error_message` | TEXT | YES | Error message if API call failed |
| `event_id` | VARCHAR(36) | YES | Keycloak event ID for direct correlation |

### Indexes

- `idx_user_id`: Index on `user_id` for fast user lookups
- `idx_realm_id`: Index on `realm_id` for realm-wide queries
- `idx_timestamp`: Index on `timestamp` for date range queries
- `idx_user_timestamp`: Composite index on `(user_id, timestamp)` for user history queries
- `idx_event_id`: Index on `event_id` for direct event correlation

### Storage Estimates

| Volume | Records/Day | Monthly Storage |
|--------|-------------|----------------|
| Small | 1,000 | ~50 MB |
| Medium | 10,000 | ~500 MB |
| Large | 100,000 | ~5 GB |

**Note**: Storage depends heavily on `raw_response` size (varies by service level).

## Common Queries

### View Recent Fraud Checks

```sql
-- Last 10 fraud checks across all realms
SELECT
    timestamp,
    username,
    email,
    ip_address,
    risk_score,
    decision,
    service_level
FROM maxmind_minfraud_check
ORDER BY timestamp DESC
LIMIT 10;
```

### Find User's Login History

```sql
-- All fraud checks for a specific user
SELECT
    timestamp,
    ip_address,
    risk_score,
    decision,
    device_session_id
FROM maxmind_minfraud_check
WHERE user_id = 'user-uuid-here'
ORDER BY timestamp DESC;
```

### Today's Blocked Logins

```sql
-- All blocked logins today
SELECT
    timestamp,
    username,
    email,
    ip_address,
    risk_score
FROM maxmind_minfraud_check
WHERE decision = 'BLOCKED'
  AND DATE(timestamp) = CURRENT_DATE
ORDER BY timestamp DESC;
```

### High Risk Logins in Last 24 Hours

```sql
-- Logins with risk score >= 70 in last 24 hours
SELECT
    timestamp,
    username,
    email,
    ip_address,
    risk_score,
    decision
FROM maxmind_minfraud_check
WHERE risk_score >= 70
  AND timestamp > NOW() - INTERVAL '24 hours'
ORDER BY risk_score DESC;
```

### API Errors

```sql
-- All API errors in the last week
SELECT
    timestamp,
    username,
    ip_address,
    error_message
FROM maxmind_minfraud_check
WHERE error_message IS NOT NULL
  AND timestamp > NOW() - INTERVAL '7 days'
ORDER BY timestamp DESC;
```

## Analytics Queries

### Daily Summary

```sql
-- Daily fraud check summary
SELECT
    DATE(timestamp) as date,
    COUNT(*) as total_checks,
    COUNT(CASE WHEN decision = 'ALLOWED' THEN 1 END) as allowed,
    COUNT(CASE WHEN decision = 'CHALLENGED' THEN 1 END) as challenged,
    COUNT(CASE WHEN decision = 'BLOCKED' THEN 1 END) as blocked,
    COUNT(CASE WHEN decision = 'ERROR' THEN 1 END) as errors,
    AVG(risk_score) FILTER (WHERE risk_score >= 0) as avg_risk_score,
    MAX(risk_score) as max_risk_score
FROM maxmind_minfraud_check
WHERE realm_id = 'your-realm-id'
  AND timestamp > NOW() - INTERVAL '30 days'
GROUP BY DATE(timestamp)
ORDER BY date DESC;
```

### Risk Score Distribution

```sql
-- Risk score distribution (histogram)
SELECT
    CASE
        WHEN risk_score < 0 THEN 'ERROR'
        WHEN risk_score <= 10 THEN '0-10'
        WHEN risk_score <= 20 THEN '11-20'
        WHEN risk_score <= 30 THEN '21-30'
        WHEN risk_score <= 40 THEN '31-40'
        WHEN risk_score <= 50 THEN '41-50'
        WHEN risk_score <= 60 THEN '51-60'
        WHEN risk_score <= 70 THEN '61-70'
        WHEN risk_score <= 80 THEN '71-80'
        WHEN risk_score <= 90 THEN '81-90'
        ELSE '91-100'
    END as risk_range,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) as percentage
FROM maxmind_minfraud_check
WHERE realm_id = 'your-realm-id'
  AND timestamp > NOW() - INTERVAL '30 days'
GROUP BY risk_range
ORDER BY risk_range;
```

### Top Risky IP Addresses

```sql
-- IP addresses with highest average risk scores
SELECT
    ip_address,
    COUNT(*) as attempt_count,
    AVG(risk_score) FILTER (WHERE risk_score >= 0) as avg_risk_score,
    MAX(risk_score) as max_risk_score,
    COUNT(CASE WHEN decision = 'BLOCKED' THEN 1 END) as blocked_count,
    MAX(timestamp) as last_attempt
FROM maxmind_minfraud_check
WHERE realm_id = 'your-realm-id'
  AND timestamp > NOW() - INTERVAL '7 days'
GROUP BY ip_address
HAVING AVG(risk_score) FILTER (WHERE risk_score >= 0) > 50
ORDER BY avg_risk_score DESC
LIMIT 20;
```

### Users with Multiple Blocked Attempts

```sql
-- Users who have been blocked multiple times
SELECT
    user_id,
    username,
    email,
    COUNT(*) as blocked_count,
    MAX(timestamp) as last_blocked,
    MIN(timestamp) as first_blocked,
    ARRAY_AGG(DISTINCT ip_address) as ip_addresses
FROM maxmind_minfraud_check
WHERE decision = 'BLOCKED'
  AND timestamp > NOW() - INTERVAL '30 days'
GROUP BY user_id, username, email
HAVING COUNT(*) >= 3
ORDER BY blocked_count DESC;
```

### Device Tracking Analysis

```sql
-- Devices with suspicious activity
SELECT
    device_session_id,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(*) as login_attempts,
    AVG(risk_score) FILTER (WHERE risk_score >= 0) as avg_risk_score,
    COUNT(CASE WHEN decision = 'BLOCKED' THEN 1 END) as blocked_count
FROM maxmind_minfraud_check
WHERE device_session_id IS NOT NULL
  AND timestamp > NOW() - INTERVAL '30 days'
GROUP BY device_session_id
HAVING COUNT(DISTINCT user_id) > 5  -- Same device used by multiple users
ORDER BY unique_users DESC, avg_risk_score DESC;
```

### Service Level Usage

```sql
-- Usage breakdown by service level
SELECT
    service_level,
    COUNT(*) as queries,
    AVG(risk_score) FILTER (WHERE risk_score >= 0) as avg_risk_score,
    COUNT(CASE WHEN decision = 'BLOCKED' THEN 1 END) as blocked_count
FROM maxmind_minfraud_check
WHERE timestamp > NOW() - INTERVAL '30 days'
GROUP BY service_level;
```

### Failed Logins Correlation

```sql
-- Compare fraud scores for successful vs failed logins
-- (Requires correlation with Keycloak event logs)
SELECT
    DATE(timestamp) as date,
    decision,
    COUNT(*) as count,
    AVG(risk_score) as avg_risk_score
FROM maxmind_minfraud_check
WHERE timestamp > NOW() - INTERVAL '7 days'
GROUP BY DATE(timestamp), decision
ORDER BY date DESC, decision;
```

### Hourly Pattern Analysis

```sql
-- Fraud check patterns by hour of day
SELECT
    EXTRACT(HOUR FROM timestamp) as hour,
    COUNT(*) as total_checks,
    AVG(risk_score) FILTER (WHERE risk_score >= 0) as avg_risk_score,
    COUNT(CASE WHEN decision = 'BLOCKED' THEN 1 END) as blocked_count
FROM maxmind_minfraud_check
WHERE realm_id = 'your-realm-id'
  AND timestamp > NOW() - INTERVAL '7 days'
GROUP BY EXTRACT(HOUR FROM timestamp)
ORDER BY hour;
```

### Geographic Analysis (Requires INSIGHTS/FACTORS)

```sql
-- Extract country from raw response (PostgreSQL with jsonb)
SELECT
    raw_response::jsonb->'ip_address'->>'country' as country,
    COUNT(*) as attempts,
    AVG(risk_score) FILTER (WHERE risk_score >= 0) as avg_risk_score,
    COUNT(CASE WHEN decision = 'BLOCKED' THEN 1 END) as blocked_count
FROM maxmind_minfraud_check
WHERE service_level IN ('INSIGHTS', 'FACTORS')
  AND raw_response IS NOT NULL
  AND timestamp > NOW() - INTERVAL '30 days'
GROUP BY country
ORDER BY attempts DESC;
```

## Event Integration

The MaxMind minFraud authenticator uses a dual logging strategy:
- **Database Table** (`maxmind_minfraud_check`): Long-term storage for analytics
- **Keycloak Events** (`event_entity`): Short-term audit trail for compliance

This section shows how to query and correlate data between both storage systems.

### Event Entity Table

Keycloak stores events in the `event_entity` table:

| Column | Type | Description |
|--------|------|-------------|
| `id` | VARCHAR(36) | Event ID (UUID) |
| `time` | BIGINT | Unix timestamp in milliseconds |
| `type` | VARCHAR(255) | Event type (LOGIN, LOGIN_ERROR, etc.) |
| `realm_id` | VARCHAR(36) | Realm ID |
| `client_id` | VARCHAR(255) | Client ID |
| `user_id` | VARCHAR(36) | User ID |
| `session_id` | VARCHAR(36) | Session ID |
| `ip_address` | VARCHAR(255) | IP address |
| `error` | VARCHAR(255) | Error message (if applicable) |
| `details` | TEXT | Key-value pairs (fraud check details stored here) |

### Correlating Events with Fraud Checks

#### By Event ID (Direct Join - Recommended)

The most accurate correlation method uses the `event_id` column for a direct join:

```sql
-- Direct join using event_id (most accurate)
SELECT
    e.id as event_id,
    to_timestamp(e.time / 1000) as event_time,
    e.type as event_type,
    e.user_id,
    e.ip_address,
    e.details as event_details,
    f.id as fraud_check_id,
    f.timestamp as fraud_check_time,
    f.risk_score,
    f.decision,
    f.request_id,
    f.service_level
FROM event_entity e
INNER JOIN maxmind_minfraud_check f
    ON e.id = f.event_id
WHERE e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY e.time DESC
LIMIT 100;
```

This method provides 100% accurate correlation since each fraud check record stores the exact event ID it's associated with.

#### By User and Time

```sql
-- Find events and fraud checks for the same user within 5 seconds
SELECT
    e.time as event_time,
    to_timestamp(e.time / 1000) as event_timestamp,
    e.type as event_type,
    e.details as event_details,
    f.id as fraud_check_id,
    f.timestamp as fraud_check_time,
    f.risk_score,
    f.decision,
    f.request_id
FROM event_entity e
LEFT JOIN maxmind_minfraud_check f
    ON e.user_id = f.user_id
    AND ABS(EXTRACT(EPOCH FROM f.timestamp) * 1000 - e.time) < 5000
WHERE e.details LIKE '%maxmind_minfraud%'
  AND e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY e.time DESC
LIMIT 100;
```

#### By Request ID

```sql
-- Correlate using MaxMind request ID (most accurate)
SELECT
    e.time,
    e.user_id,
    e.ip_address,
    e.details,
    f.risk_score,
    f.decision,
    f.timestamp,
    f.raw_response
FROM event_entity e
INNER JOIN maxmind_minfraud_check f
    ON e.user_id = f.user_id
    AND e.details LIKE '%' || f.request_id || '%'
WHERE f.request_id IS NOT NULL
  AND e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY e.time DESC;
```

### Comparing Event and Database Counts

```sql
-- Compare record counts between events and database (using event_id for accurate matching)
SELECT
    (SELECT COUNT(*)
     FROM event_entity
     WHERE details LIKE '%maxmind_minfraud%'
       AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000) as event_count,
    (SELECT COUNT(*)
     FROM maxmind_minfraud_check
     WHERE timestamp > NOW() - INTERVAL '7 days') as db_count,
    (SELECT COUNT(*)
     FROM event_entity e
     INNER JOIN maxmind_minfraud_check f
         ON e.id = f.event_id
     WHERE e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000) as matched_count;
```

### Extracting Event Details

Event details are stored as comma-separated key-value pairs in the `details` column:

```
maxmind_minfraud_risk_score=45.50,maxmind_minfraud_risk_level=MEDIUM,maxmind_minfraud_decision=CHALLENGE,...
```

#### Extract Specific Event Detail

```sql
-- Extract risk score from event details (PostgreSQL)
SELECT
    time,
    user_id,
    SUBSTRING(details FROM 'maxmind_minfraud_risk_score=([0-9.]+)') as risk_score,
    SUBSTRING(details FROM 'maxmind_minfraud_risk_level=([A-Z]+)') as risk_level,
    SUBSTRING(details FROM 'maxmind_minfraud_decision=([A-Z]+)') as decision
FROM event_entity
WHERE details LIKE '%maxmind_minfraud%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000
ORDER BY time DESC;
```

#### Parse All Event Details

```sql
-- Parse all fraud-related event details (PostgreSQL)
WITH parsed_events AS (
    SELECT
        time,
        user_id,
        ip_address,
        SUBSTRING(details FROM 'maxmind_minfraud_risk_score=([0-9.]+)')::NUMERIC as risk_score,
        SUBSTRING(details FROM 'maxmind_minfraud_risk_level=([A-Z]+)') as risk_level,
        SUBSTRING(details FROM 'maxmind_minfraud_decision=([A-Z]+)') as decision,
        SUBSTRING(details FROM 'maxmind_minfraud_request_id=([a-f0-9-]+)') as request_id,
        SUBSTRING(details FROM 'maxmind_minfraud_service_level=([A-Z]+)') as service_level
    FROM event_entity
    WHERE details LIKE '%maxmind_minfraud%'
      AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
)
SELECT * FROM parsed_events
WHERE risk_score > 70
ORDER BY risk_score DESC;
```

### Daily Summary from Both Sources

```sql
-- Compare daily summaries from events vs database
SELECT
    'Database' as source,
    DATE(timestamp) as date,
    COUNT(*) as total,
    COUNT(CASE WHEN decision = 'ALLOWED' THEN 1 END) as allowed,
    COUNT(CASE WHEN decision = 'CHALLENGED' THEN 1 END) as challenged,
    COUNT(CASE WHEN decision = 'BLOCKED' THEN 1 END) as blocked,
    AVG(risk_score) FILTER (WHERE risk_score >= 0) as avg_risk_score
FROM maxmind_minfraud_check
WHERE timestamp > NOW() - INTERVAL '7 days'
GROUP BY DATE(timestamp)

UNION ALL

SELECT
    'Events' as source,
    DATE(to_timestamp(time / 1000)) as date,
    COUNT(*) as total,
    COUNT(CASE WHEN details LIKE '%decision=ALLOW%' THEN 1 END) as allowed,
    COUNT(CASE WHEN details LIKE '%decision=CHALLENGE%' THEN 1 END) as challenged,
    COUNT(CASE WHEN details LIKE '%decision=BLOCK%' THEN 1 END) as blocked,
    AVG(SUBSTRING(details FROM 'risk_score=([0-9.]+)')::NUMERIC) as avg_risk_score
FROM event_entity
WHERE details LIKE '%maxmind_minfraud%'
  AND time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
GROUP BY DATE(to_timestamp(time / 1000))

ORDER BY date DESC, source;
```

### High-Risk Events with Full Context

```sql
-- High-risk events with complete fraud check data (using event_id for accurate join)
SELECT
    to_timestamp(e.time / 1000) as event_time,
    e.type as event_type,
    e.user_id,
    e.ip_address,
    e.details as event_details,
    f.timestamp as fraud_check_time,
    f.username,
    f.email,
    f.risk_score,
    f.decision,
    f.request_id,
    f.service_level,
    f.raw_response
FROM event_entity e
INNER JOIN maxmind_minfraud_check f
    ON e.id = f.event_id
WHERE f.risk_score >= 70  -- High risk threshold
  AND e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY e.time DESC;
```

### Finding Discrepancies

```sql
-- Find fraud checks without corresponding events (using event_id)
SELECT
    f.id,
    f.timestamp,
    f.user_id,
    f.username,
    f.risk_score,
    f.decision,
    f.event_id
FROM maxmind_minfraud_check f
LEFT JOIN event_entity e
    ON f.event_id = e.id
WHERE f.timestamp > NOW() - INTERVAL '1 day'
  AND f.event_id IS NOT NULL
  AND e.id IS NULL
ORDER BY f.timestamp DESC;

-- Find events without corresponding database records
SELECT
    to_timestamp(e.time / 1000) as event_time,
    e.user_id,
    e.ip_address,
    e.details
FROM event_entity e
LEFT JOIN maxmind_minfraud_check f
    ON e.user_id = f.user_id
    AND ABS(EXTRACT(EPOCH FROM f.timestamp) * 1000 - e.time) < 5000
WHERE e.details LIKE '%maxmind_minfraud%'
  AND e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '1 day') * 1000
  AND f.id IS NULL
ORDER BY e.time DESC;
```

### MFA Enforcer Events

When the **MaxMind MFA Enforcer** authenticator executes, it logs events to Keycloak's event system with detailed information about MFA enforcement decisions.

**Event Detail Keys**:

| Detail Key | Description | Example Value |
|-----------|-------------|---------------|
| `maxmind_mfa_enforcer_decision` | Enforcement decision | `ALLOWED`, `BLOCKED` |
| `maxmind_mfa_enforcer_type` | MFA type found (if ALLOWED) | `otp`, `webauthn` |
| `maxmind_mfa_enforcer_reason` | Reason for block (if BLOCKED) | `No MFA configured` |
| `maxmind_mfa_enforcer_risk_score` | Original fraud risk score | `65.00`, `unknown` |
| `maxmind_mfa_enforcer_checked_types` | Credential types checked | `otp,webauthn` |
| `auth_method` | Authentication method identifier | `maxmind_mfa_enforcer` |

**Example Event Details** (ALLOWED):
```
maxmind_mfa_enforcer_decision=ALLOWED
maxmind_mfa_enforcer_type=otp
maxmind_mfa_enforcer_risk_score=45.50
auth_method=maxmind_mfa_enforcer
```

**Example Event Details** (BLOCKED):
```
maxmind_mfa_enforcer_decision=BLOCKED
maxmind_mfa_enforcer_reason=No MFA configured
maxmind_mfa_enforcer_risk_score=65.00
maxmind_mfa_enforcer_checked_types=otp,webauthn
auth_method=maxmind_mfa_enforcer
```

#### Query MFA Enforcer Blocked Attempts

Find users who were blocked for not having MFA during suspicious logins:

```sql
-- Users blocked by MFA Enforcer in last 7 days
SELECT
    to_timestamp(e.time / 1000) as event_time,
    e.user_id,
    u.username,
    u.email,
    e.ip_address,
    SUBSTRING(e.details FROM 'maxmind_mfa_enforcer_risk_score=([0-9.]+)') as risk_score,
    SUBSTRING(e.details FROM 'maxmind_mfa_enforcer_checked_types=([^,]+)') as checked_types
FROM event_entity e
JOIN user_entity u ON e.user_id = u.id
WHERE e.details LIKE '%maxmind_mfa_enforcer_decision=BLOCKED%'
  AND e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '7 days') * 1000
ORDER BY e.time DESC;
```

#### Query MFA Adoption for Risk-Based Authentication

Track which users have MFA and are protected from MFA Enforcer blocks:

```sql
-- MFA adoption rate among users with fraud check history
SELECT
    u.id,
    u.username,
    u.email,
    COUNT(DISTINCT f.id) as fraud_checks,
    MAX(f.risk_score) as max_risk_score,
    COUNT(DISTINCT CASE WHEN c.type IN ('otp', 'webauthn') THEN c.id END) as mfa_credentials,
    CASE
        WHEN COUNT(DISTINCT CASE WHEN c.type IN ('otp', 'webauthn') THEN c.id END) > 0
        THEN 'Protected'
        ELSE 'At Risk'
    END as mfa_status
FROM user_entity u
LEFT JOIN maxmind_minfraud_check f ON u.id = f.user_id
LEFT JOIN credential c ON u.id = c.user_id
WHERE f.timestamp > NOW() - INTERVAL '30 days'
GROUP BY u.id, u.username, u.email
ORDER BY max_risk_score DESC;
```

#### Monitor MFA Enforcer Effectiveness

Track how many users are being blocked vs allowed:

```sql
-- MFA Enforcer decisions summary (last 24 hours)
SELECT
    SUBSTRING(e.details FROM 'maxmind_mfa_enforcer_decision=([A-Z]+)') as decision,
    COUNT(*) as count,
    COUNT(DISTINCT e.user_id) as unique_users,
    AVG(CAST(SUBSTRING(e.details FROM 'maxmind_mfa_enforcer_risk_score=([0-9.]+)') AS DECIMAL)) as avg_risk_score
FROM event_entity e
WHERE e.details LIKE '%maxmind_mfa_enforcer_decision=%'
  AND e.time > EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000
GROUP BY decision
ORDER BY count DESC;
```

### Event vs Database Trade-offs

**Configuration Note**: Database recording can be disabled via the **Record Fraud Checks to Database** configuration option. When disabled, only events are logged. See [CONFIGURATION.md](CONFIGURATION.md#database-recording) for details.

| Feature | Events (`event_entity`) | Database (`maxmind_minfraud_check`) |
|---------|------------------------|-------------------------------------|
| **Configuration** | Always enabled (if Save Events enabled) | Optional (enabled by default) |
| **Retention** | Short-term (days to weeks) | Long-term (months to years) |
| **Purpose** | Compliance audit trail | Analytics and reporting |
| **Query Performance** | Slower (parsing details) | Faster (indexed columns) |
| **Admin UI** | Available in Keycloak | SQL only |
| **Data Structure** | Key-value in `details` | Structured columns |
| **Storage Size** | Smaller | Larger (`raw_response` column) |
| **Event Listeners** | Yes (real-time alerts) | No |

**Note**: MFA Enforcer events are logged to `event_entity` only, not to the `maxmind_minfraud_check` table, as they represent authentication flow decisions rather than fraud check results.

### Best Practices

1. **Use Events for Compliance**:
   - Enable Save Events if you need audit trails
   - Set expiration to 30-90 days for compliance
   - Use event listeners for real-time alerts

2. **Use Database for Analytics**:
   - Query `maxmind_minfraud_check` for historical analysis
   - Build dashboards from database table
   - Export data for machine learning

3. **Correlate When Needed**:
   - Use event_id for most accurate correlation (direct join on e.id = f.event_id)
   - Fallback to request_id if event_id is null (legacy data)
   - Events provide authentication context (session, client)
   - Database provides full fraud check details

4. **Monitor Discrepancies**:
   - Counts should match within 1-2%
   - Large gaps indicate storage issues
   - Check Keycloak logs if discrepancies exist

## Data Retention

### Archive Old Records

```sql
-- Create archive table
CREATE TABLE maxmind_minfraud_check_archive (
    LIKE maxmind_minfraud_check INCLUDING ALL
);

-- Move records older than 90 days to archive
INSERT INTO maxmind_minfraud_check_archive
SELECT * FROM maxmind_minfraud_check
WHERE timestamp < NOW() - INTERVAL '90 days';

-- Delete archived records from main table
DELETE FROM maxmind_minfraud_check
WHERE timestamp < NOW() - INTERVAL '90 days';
```

### Automated Archival (PostgreSQL)

```sql
-- Create function to archive old records
CREATE OR REPLACE FUNCTION archive_fraud_checks()
RETURNS void AS $$
BEGIN
    INSERT INTO maxmind_minfraud_check_archive
    SELECT * FROM maxmind_minfraud_check
    WHERE timestamp < NOW() - INTERVAL '90 days'
    ON CONFLICT DO NOTHING;

    DELETE FROM maxmind_minfraud_check
    WHERE timestamp < NOW() - INTERVAL '90 days';
END;
$$ LANGUAGE plpgsql;

-- Schedule with pg_cron (requires pg_cron extension)
SELECT cron.schedule('archive-fraud-checks', '0 2 * * 0', 'SELECT archive_fraud_checks()');
```

### Export to CSV

```sql
-- Export last 30 days to CSV (PostgreSQL)
COPY (
    SELECT * FROM maxmind_minfraud_check
    WHERE timestamp > NOW() - INTERVAL '30 days'
) TO '/tmp/fraud_checks.csv' WITH CSV HEADER;
```

## Performance Optimization

### Add Indexes for Custom Queries

```sql
-- If you frequently query by IP address
CREATE INDEX idx_ip_address ON maxmind_minfraud_check(ip_address);

-- If you frequently query by decision and timestamp
CREATE INDEX idx_decision_timestamp ON maxmind_minfraud_check(decision, timestamp);

-- If you frequently query by realm and risk score
CREATE INDEX idx_realm_risk ON maxmind_minfraud_check(realm_id, risk_score);
```

### Partitioning (PostgreSQL 11+)

For high-volume environments, partition by month:

```sql
-- Create partitioned table
CREATE TABLE maxmind_minfraud_check_partitioned (
    LIKE maxmind_minfraud_check INCLUDING ALL
) PARTITION BY RANGE (timestamp);

-- Create partitions
CREATE TABLE maxmind_minfraud_check_2024_01
    PARTITION OF maxmind_minfraud_check_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE maxmind_minfraud_check_2024_02
    PARTITION OF maxmind_minfraud_check_partitioned
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- Create partitions automatically with pg_partman extension
```

### Query Optimization Tips

1. **Always include timestamp filters** - Uses the timestamp index
2. **Filter by realm_id** - Reduces scan size in multi-tenant setups
3. **Use LIMIT** - Avoid scanning entire table
4. **Avoid SELECT *** - Especially with large `raw_response` column
5. **Use FILTER for conditional aggregates** - Faster than CASE WHEN

### Vacuum and Analyze

```sql
-- Regular maintenance (PostgreSQL)
VACUUM ANALYZE maxmind_minfraud_check;

-- Schedule auto-vacuum (adjust postgresql.conf)
-- autovacuum = on
-- autovacuum_vacuum_scale_factor = 0.1
```

## Visualization and Dashboards

### Grafana Integration

Create a PostgreSQL datasource in Grafana and use queries above to build dashboards.

**Sample Dashboard Panels**:
- Fraud checks per hour (time series)
- Decision breakdown (pie chart)
- Risk score distribution (histogram)
- Top risky IPs (table)
- Blocked logins over time (graph)

### Metabase / Superset

Import the database and create automated reports:
- Daily fraud summary email
- Weekly risk score trends
- Monthly security report

## Next Steps

- See [CONFIGURATION.md](CONFIGURATION.md) for tuning risk thresholds based on analytics
- See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for investigating issues
- Export data to your SIEM or log aggregation platform for centralized monitoring
