# Detailed Setup Guide

This guide provides step-by-step instructions for installing and configuring the Keycloak MaxMind minFraud extension.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [MaxMind Account Setup](#maxmind-account-setup)
3. [Building the Extension](#building-the-extension)
4. [Deploying to Keycloak](#deploying-to-keycloak)
5. [Configuring Authentication Flow](#configuring-authentication-flow)
6. [Testing the Integration](#testing-the-integration)
7. [Production Deployment](#production-deployment)

## Prerequisites

### Software Requirements

- **Keycloak**: Version 22.0.0 or higher (tested up to 26.x)
- **Java**: JDK 17 or higher
- **Maven**: Version 3.6 or higher
- **Database**: One of the following:
  - PostgreSQL 12+
  - MySQL 8.0+
  - MariaDB 10.5+
  - Any database supported by Keycloak

### Version Compatibility

This extension (v1.0+) requires **Keycloak 22+** due to the Jakarta EE migration:

| Extension Version | Keycloak Versions | Java Version | Namespace |
|------------------|-------------------|--------------|-----------|
| v1.0+ (current) | 22.0.0 - 26.x+ | Java 17+ | jakarta.* |
| v0.1-SNAPSHOT | 17.x - 21.x | Java 11+ | javax.* |

If you're using Keycloak 17-21, please use version 0.1-SNAPSHOT.

### MaxMind Requirements

- Active MaxMind account with minFraud subscription
- API access enabled
- Valid credit card on file (if using paid tier)

## MaxMind Account Setup

### Step 1: Create MaxMind Account

1. Go to https://www.maxmind.com/en/geolite2/signup
2. Fill out the registration form
3. Verify your email address
4. Complete your profile

### Step 2: Subscribe to minFraud Service

1. Navigate to https://www.maxmind.com/en/solutions/minfraud-services
2. Choose a service level:
   - **minFraud Score**: Basic risk score ($0.005 per query)
   - **minFraud Insights**: Score + detailed data ($0.01 per query)
   - **minFraud Factors**: Most comprehensive ($0.02 per query)
3. Complete the subscription process
4. Add payment method

### Step 3: Generate License Key

1. Log into your MaxMind account portal
2. Navigate to **Account** → **Manage License Keys**
3. Click **Generate new license key**
4. Enter a description: "Keycloak minFraud Integration"
5. Select **maxmindfr-minfraud-user-token** for the license key type
6. Click **Confirm**
7. **IMPORTANT**: Copy the license key immediately (it will only be shown once)
8. Note your Account ID (shown in the account portal)

### Step 4: Configure IP Allowlist (Optional)

If your Keycloak server has a static IP:

1. Navigate to **Account** → **Security**
2. Add your Keycloak server's public IP to the allowlist
3. This adds an extra layer of security

## Building the Extension

### Clone the Repository

```bash
git clone https://github.com/your-org/keycloak-maxmind.git
cd keycloak-maxmind
```

### Build with Maven

```bash
mvn clean package
```

This will:
- Download dependencies
- Compile the code
- Run tests
- Create `target/zymlabs-maxmind-provider.jar`

Expected output:
```
[INFO] BUILD SUCCESS
[INFO] Total time:  12.345 s
[INFO] Finished at: 2024-01-15T10:30:00Z
```

### Verify the Build

```bash
ls -lh target/zymlabs-maxmind-provider.jar
```

Should show a JAR file approximately 50-100 KB in size.

## Deploying to Keycloak

### For Keycloak Quarkus (v17+)

#### Step 1: Stop Keycloak

```bash
cd /path/to/keycloak
bin/kc.sh stop
```

#### Step 2: Copy JAR to Providers Directory

```bash
cp /path/to/keycloak-maxmind/target/zymlabs-maxmind-provider.jar providers/
```

#### Step 3: Rebuild Keycloak (if needed)

```bash
bin/kc.sh build
```

Output should include:
```
Updating the configuration and installing your custom providers, if any. Please wait.
```

#### Step 4: Start Keycloak

```bash
# Development mode
bin/kc.sh start-dev

# Production mode (requires additional config)
bin/kc.sh start --optimized
```

### For Keycloak WildFly (Legacy)

#### Step 1: Stop Keycloak

```bash
cd /path/to/keycloak
bin/standalone.sh -c standalone.xml --admin-only
```

Then run:
```bash
:shutdown
```

#### Step 2: Deploy JAR

```bash
cp /path/to/keycloak-maxmind/target/zymlabs-maxmind-provider.jar standalone/deployments/
```

#### Step 3: Start Keycloak

```bash
bin/standalone.sh -b 0.0.0.0
```

### Verify Deployment

#### Check Logs

Look for these log messages:

```
INFO  [com.zymlabs.keycloak.maxmindprovider.MaxMindMinFraudCheckProviderFactory] Initializing MaxMindMinFraudCheckProviderFactory
INFO  [com.zymlabs.keycloak.maxmindprovider.MaxMindMinFraudCheckProviderFactory] MaxMindMinFraudCheckProviderFactory post-initialization complete
```

#### Check Database

Connect to your Keycloak database and verify the table was created:

```sql
\d maxmind_minfraud_check  -- PostgreSQL
DESCRIBE maxmind_minfraud_check;  -- MySQL
```

## Configuring Authentication Flow

### Step 1: Access Admin Console

1. Navigate to http://your-keycloak:8080/admin
2. Log in with admin credentials

### Step 2: Create Custom Authentication Flow

1. Click on your realm (or use "master" for testing)
2. Navigate to **Authentication** → **Flows**
3. Click **Copy** on the "Browser" flow
4. Name it "Browser with MaxMind"
5. Click **OK**

### Step 3: Add MaxMind Authenticator

1. In your new flow, click **Add execution**
2. Select **MaxMind minFraud** from the dropdown
3. Click **Add**
4. Set the requirement to **REQUIRED**
5. Use the up/down arrows to position it after "Username Password Form"

Your flow should look like:
```
Browser with MaxMind
├── Cookie (ALTERNATIVE)
├── Kerberos (DISABLED)
└── Browser with MaxMind Forms (ALTERNATIVE)
    ├── Username Password Form (REQUIRED)
    └── MaxMind minFraud (REQUIRED)
    └── OTP Form (CONDITIONAL)
```

### Step 4: Configure MaxMind Authenticator

1. Click the **Actions** (⚙️) button next to "MaxMind minFraud"
2. Click **Config**
3. Enter an alias: "maxmind-config"
4. Fill in the configuration:

#### Basic Configuration

| Field | Value | Example |
|-------|-------|---------|
| MaxMind Account ID | Your account ID | 123456 |
| MaxMind License Key | Your license key | xK7j...9Lm2 |
| Service Level | SCORE, INSIGHTS, or FACTORS | SCORE |
| Enable Device Tracking | true or false | false |
| Record Fraud Checks to Database | true or false | true |

#### Risk Thresholds

| Field | Recommended Value | Notes |
|-------|------------------|-------|
| Low Risk Threshold | 30 | Scores 0-30 are low risk |
| High Risk Threshold | 70 | Scores 71+ are high risk |

#### Risk Actions

| Risk Level | Recommended Action | Notes |
|------------|-------------------|-------|
| Low Risk | ALLOW | Normal login |
| Medium Risk | CHALLENGE | Require MFA |
| High Risk | BLOCK | Deny login |

#### Error Handling

| Field | Recommended Value | Notes |
|-------|------------------|-------|
| API Failure Mode | FAIL_OPEN | For development |
|                  | FAIL_CLOSED | For production |

5. Click **Save**

### Step 5: Bind the Flow

1. Navigate to **Authentication** → **Bindings**
2. Set **Browser Flow** to "Browser with MaxMind"
3. Click **Save**

## Testing the Integration

### Step 1: Create Test User

1. Navigate to **Users** → **Add user**
2. Username: testuser
3. Email: test@example.com
4. Click **Save**
5. Go to **Credentials** tab
6. Set password: test123
7. Disable **Temporary**
8. Click **Set password**

### Step 2: Test Login

1. Open a new incognito/private browser window
2. Navigate to your Keycloak account console or test application
3. Log in with testuser / test123
4. Login should succeed (assuming low risk score)

### Step 3: Verify in Database

Check that a fraud check record was created (only if **Record Fraud Checks to Database** is enabled, which is the default):

```sql
SELECT * FROM maxmind_minfraud_check
ORDER BY timestamp DESC
LIMIT 1;
```

**Note**: If you disabled database recording, this table will be empty but fraud checks are still logged to Keycloak events.

You should see:
- User ID
- IP address
- Risk score
- Decision (ALLOWED, CHALLENGED, or BLOCKED)
- Raw MaxMind response

### Step 4: Check Keycloak Logs

Look for log entries:

```
INFO  [com.zymlabs.keycloak.maxmindprovider.MaxMindMinFraudAuthenticator] Performing fraud check for user testuser (ID: abc-123) from IP: 192.168.1.100
INFO  [com.zymlabs.keycloak.maxmindprovider.MaxMindMinFraudService] Score API response: risk_score=15.5, request_id=xyz-789
INFO  [com.zymlabs.keycloak.maxmindprovider.MaxMindMinFraudAuthenticator] Risk level: LOW (score=15.5), Action: ALLOW
```

### Step 5: Verify Event Logging (Optional but Recommended)

The extension logs fraud checks to both the database and Keycloak's event system. To verify event logging:

#### Enable Event Storage

1. Navigate to **Realm Settings** → **Events** → **User Events Settings**
2. Enable **Save Events**
3. Set **Expiration** to 30 days
4. Click **Save**

#### View Events in Admin Console

1. Navigate to **Realm Settings** → **Events** → **User Events**
2. You should see a LOGIN event for your test login
3. Click on the event to expand details
4. Look for fraud check details in the event:
   - `maxmind_minfraud_risk_score`: 15.50
   - `maxmind_minfraud_risk_level`: LOW
   - `maxmind_minfraud_decision`: ALLOW
   - `maxmind_minfraud_request_id`: (UUID)
   - `maxmind_minfraud_service_level`: SCORE
   - `auth_method`: maxmind_minfraud

#### Verify Events in Database

```sql
-- View fraud check events
SELECT time, user_id, ip_address, details
FROM event_entity
WHERE details LIKE '%maxmind_minfraud%'
ORDER BY time DESC
LIMIT 5;
```

You should see events with fraud check details in the `details` column.

#### Correlate Events with Fraud Check Records

```sql
-- Match events with database records
SELECT
    to_timestamp(e.time / 1000) as event_time,
    e.details as event_details,
    f.timestamp as fraud_check_time,
    f.risk_score,
    f.decision
FROM event_entity e
INNER JOIN maxmind_minfraud_check f
    ON e.user_id = f.user_id
    AND ABS(EXTRACT(EPOCH FROM f.timestamp) * 1000 - e.time) < 5000
WHERE e.details LIKE '%maxmind_minfraud%'
ORDER BY e.time DESC
LIMIT 5;
```

Both timestamps should match (within a few seconds), confirming dual logging is working correctly.

### Step 6: Test Different Risk Scenarios

To test blocking:

1. Temporarily set Low Risk Threshold to 0 and Low Risk Action to BLOCK
2. Try logging in again
3. Login should be blocked
4. Reset configuration to original values

## Production Deployment

### Security Checklist

- [ ] Use FAIL_CLOSED mode for production
- [ ] Set strong risk thresholds based on your security requirements
- [ ] Enable Device Tracking for enhanced detection
- [ ] Configure IP allowlist in MaxMind portal (if using static IPs)
- [ ] Secure Keycloak database (contains license keys)
- [ ] Enable HTTPS for Keycloak
- [ ] Set up monitoring for fraud events
- [ ] Configure log aggregation
- [ ] Document your incident response process

### Performance Considerations

- MaxMind API typically responds in 50-200ms
- Consider impact on login latency
- Monitor MaxMind usage in your account portal
- Set up alerts for high usage or API errors

### Monitoring Setup

#### Database Monitoring

Create a view for easy monitoring:

```sql
CREATE VIEW fraud_check_summary AS
SELECT
    DATE(timestamp) as date,
    decision,
    COUNT(*) as count,
    AVG(risk_score) as avg_risk_score
FROM maxmind_minfraud_check
GROUP BY DATE(timestamp), decision
ORDER BY date DESC;
```

#### Set Up Alerts

Monitor for:
- High number of BLOCKED logins
- API errors (error_message IS NOT NULL)
- Unusual risk score patterns
- MaxMind API rate limit warnings

### Backup Considerations

The `maxmind_minfraud_check` table grows over time. Consider:

- Regular archival of old records (e.g., keep last 90 days)
- Separate table partitioning by date
- Export to data warehouse for long-term analytics

## Next Steps

- Review [CONFIGURATION.md](CONFIGURATION.md) for advanced configuration options
- See [DATABASE.md](DATABASE.md) for analytics queries
- Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md) if you encounter issues

## Support

If you need help:
- Check the [Troubleshooting Guide](TROUBLESHOOTING.md)
- Review Keycloak logs
- Check MaxMind account status
- Open a GitHub issue with logs and configuration (redact sensitive info)
