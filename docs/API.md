# MaxMind minFraud API Integration

Technical details about the MaxMind minFraud API integration in the Keycloak extension.

## Table of Contents

1. [API Overview](#api-overview)
2. [Service Levels](#service-levels)
3. [Request Format](#request-format)
4. [Response Format](#response-format)
5. [Error Handling](#error-handling)
6. [Rate Limits](#rate-limits)
7. [Best Practices](#best-practices)

## API Overview

### Endpoints

The extension uses the following MaxMind minFraud API endpoints:

- **Score**: `https://minfraud.maxmind.com/minfraud/v2.0/score`
- **Insights**: `https://minfraud.maxmind.com/minfraud/v2.0/insights`
- **Factors**: `https://minfraud.maxmind.com/minfraud/v2.0/factors`

### Authentication

HTTP Basic Authentication using:
- Username: MaxMind Account ID
- Password: MaxMind License Key

### SDK Used

This extension uses the official MaxMind minFraud Java SDK:
- Group ID: `com.maxmind.minfraud`
- Artifact ID: `minfraud`
- Version: 1.16.0
- Documentation: https://maxmind.github.io/minfraud-api-java/

## Service Levels

### minFraud Score

**Endpoint**: `/minfraud/v2.0/score`
**Cost**: ~$0.005 per query
**Response Time**: ~50-100ms

**Returns**:
- Risk score (0-100)
- Request ID
- Warning messages (if any)

**Use Cases**:
- High-volume environments
- Cost-sensitive deployments
- Basic fraud detection
- Initial rollout/testing

### minFraud Insights

**Endpoint**: `/minfraud/v2.0/insights`
**Cost**: ~$0.01 per query
**Response Time**: ~100-200ms

**Returns** (Score data plus):
- IP geolocation (country, city, coordinates)
- IP risk data (is_anonymous, is_anonymous_vpn, is_tor_exit_node)
- Email domain risk
- Device information (if device tracking enabled)
- Billing/shipping address validation

**Use Cases**:
- Geographic restrictions
- VPN/proxy detection
- Device reputation tracking
- Moderate security requirements

### minFraud Factors

**Endpoint**: `/minfraud/v2.0/factors`
**Cost**: ~$0.02 per query
**Response Time**: ~150-250ms

**Returns** (Insights data plus):
- Detailed risk factor subscores
  - email_address subscore
  - ip_address subscore
  - billing_address subscore
  - device subscore
  - time subscore
- Risk score reasons (why score is high/low)
- Device ID for tracking
- Email domain details

**Use Cases**:
- Maximum fraud prevention
- Compliance/regulatory requirements
- Detailed forensic analysis
- Fine-tuned risk management

## Request Format

### Basic Request (Score)

```json
{
  "device": {
    "ip_address": "203.0.113.45"
  },
  "event": {
    "transaction_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### Request with Email

```json
{
  "device": {
    "ip_address": "203.0.113.45"
  },
  "email": {
    "address": "user@example.com"
  },
  "event": {
    "transaction_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### Request with Device Tracking

```json
{
  "device": {
    "ip_address": "203.0.113.45",
    "session_id": "abc-123-def-456"
  },
  "email": {
    "address": "user@example.com"
  },
  "event": {
    "transaction_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### Full Request (All Available Fields)

```json
{
  "device": {
    "ip_address": "203.0.113.45",
    "user_agent": "Mozilla/5.0...",
    "accept_language": "en-US,en;q=0.9",
    "session_id": "abc-123-def-456",
    "session_age": 3600.5
  },
  "email": {
    "address": "user@example.com",
    "domain": "example.com"
  },
  "billing": {
    "first_name": "John",
    "last_name": "Doe",
    "address": "123 Main St",
    "city": "New York",
    "region": "NY",
    "country": "US",
    "postal": "10001"
  },
  "event": {
    "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
    "shop_id": "keycloak",
    "time": "2024-01-15T10:30:00Z",
    "type": "account_login"
  },
  "account": {
    "user_id": "user-uuid",
    "username_md5": "098f6bcd4621d373cade4e832627b4f6"
  }
}
```

**Note**: The current Keycloak extension sends a minimal request (IP, email, device session ID). Future versions could support additional fields.

## Response Format

### Score Response

```json
{
  "id": "5bc5d6c2-b2c8-40af-87f4-6d61af86b6ae",
  "risk_score": 15.5,
  "warnings": []
}
```

### Insights Response

```json
{
  "id": "5bc5d6c2-b2c8-40af-87f4-6d61af86b6ae",
  "risk_score": 45.2,
  "ip_address": {
    "country": {
      "code": "US",
      "name": "United States"
    },
    "city": {
      "name": "New York"
    },
    "location": {
      "latitude": 40.7128,
      "longitude": -74.0060
    },
    "is_anonymous": false,
    "is_anonymous_vpn": false,
    "is_tor_exit_node": false,
    "is_hosting_provider": false,
    "is_public_proxy": false,
    "risk": 8
  },
  "email": {
    "is_free": true,
    "is_high_risk": false
  },
  "device": {
    "confidence": 82,
    "id": "abc123"
  },
  "warnings": []
}
```

### Factors Response

```json
{
  "id": "5bc5d6c2-b2c8-40af-87f4-6d61af86b6ae",
  "risk_score": 72.8,
  "subscores": {
    "email_address": 12.5,
    "ip_address": 25.3,
    "device": 18.7,
    "billing_address": 8.2,
    "time": 8.1
  },
  "risk_score_reasons": [
    {"code": "ANONYMOUS_IP", "reason": "The IP address is an anonymous proxy"},
    {"code": "HIGH_RISK_EMAIL", "reason": "Email domain has high fraud rate"}
  ],
  "ip_address": {
    "country": {"code": "RU", "name": "Russia"},
    "is_anonymous": true,
    "is_anonymous_vpn": true,
    "risk": 95
  },
  "email": {
    "is_free": true,
    "is_high_risk": true,
    "is_disposable": true
  },
  "device": {
    "confidence": 95,
    "id": "xyz789",
    "last_seen": "2024-01-10T08:15:00Z",
    "local_time": "2024-01-15T15:30:00+03:00"
  },
  "warnings": []
}
```

## Error Handling

### Common Error Responses

#### Invalid Credentials (401)

```json
{
  "code": "AUTHORIZATION_INVALID",
  "error": "Invalid account ID or license key"
}
```

**Extension Handling**:
- Logs error
- Stores check with error_message
- Fails based on fail mode (FAIL_OPEN vs FAIL_CLOSED)

#### Insufficient Funds (402)

```json
{
  "code": "INSUFFICIENT_FUNDS",
  "error": "Insufficient funds on your account"
}
```

**Extension Handling**:
- Same as 401

#### Permission Required (403)

```json
{
  "code": "PERMISSION_REQUIRED",
  "error": "Your IP address is not allowed"
}
```

**Extension Handling**:
- Logs IP restriction error
- Same as 401

#### Invalid Input (400)

```json
{
  "code": "IP_ADDRESS_INVALID",
  "error": "The IP address provided is invalid"
}
```

**Extension Handling**:
- Logs validation error
- Stores check with error
- Fails based on fail mode

### Network Errors

#### Connection Timeout

**Cause**: Keycloak can't reach MaxMind API

**Extension Handling**:
- Catches `ConnectException`
- Logs "Error calling MaxMind minFraud API"
- Returns FraudCheckResult with error message

#### SSL/TLS Errors

**Cause**: Certificate validation failure

**Extension Handling**:
- Catches `SSLException`
- Logs certificate error
- Same as timeout

### Extension Error Handling Strategy

```java
try {
    // Call MaxMind API
    result = service.checkFraud(ipAddress, email, deviceSessionId);

    if (result.isSuccess()) {
        // Process risk score
    } else {
        // API returned error
        if (failMode == FailMode.FAIL_CLOSED) {
            // Block login
            context.failure(AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR);
        } else {
            // Allow login
            context.success();
        }
    }
} catch (Exception e) {
    // Network/unexpected error
    logger.error("Unexpected error during fraud check", e);
    // Handle based on fail mode
}
```

## Rate Limits

### API Limits

MaxMind enforces rate limits to prevent abuse:

- **Queries per second**: 200 QPS (default, can be increased)
- **Burst limit**: Up to 500 concurrent requests
- **Daily limit**: Based on your plan and credits

### Handling Rate Limits

When rate limit is exceeded, MaxMind returns:

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "error": "Too many requests"
}
```

**Recommended Actions**:

1. **Monitor usage** in MaxMind portal
2. **Set up alerts** for approaching limits
3. **Implement exponential backoff** (future enhancement)
4. **Consider caching** for repeated IP checks
5. **Upgrade plan** if consistently hitting limits

### Extension Usage Patterns

Estimate your needs:

| Daily Logins | Monthly Queries | Estimated Cost (Score) |
|--------------|-----------------|------------------------|
| 1,000 | 30,000 | $150 |
| 10,000 | 300,000 | $1,500 |
| 100,000 | 3,000,000 | $15,000 |

**Note**: One fraud check = one API query

## Best Practices

### 1. Start with Score

Begin with minFraud Score service level:
- Lowest cost
- Fastest response
- Sufficient for initial deployment

Upgrade to Insights/Factors after analyzing results.

### 2. Monitor API Usage

Track queries in MaxMind portal:
- **Account** → **Usage**
- Set up email alerts for thresholds
- Export usage reports monthly

### 3. Use Device Tracking

Enable device tracking for:
- Better fraud detection
- Device reputation building
- Repeat offender identification

Requires Insights or Factors service level.

### 4. Send Additional Data

The extension currently sends minimal data. Consider enhancing to send:
- User agent
- Accept language
- Account age
- Previous login history
- Geographic consistency

This improves accuracy.

### 5. Handle Errors Gracefully

**Development**: Use FAIL_OPEN
- API errors don't block development
- Easy testing and debugging

**Production**: Consider FAIL_CLOSED
- Maximum security
- Accept potential false positives during API issues
- Have monitoring and alerting in place

### 6. Store Raw Responses

The extension stores full API responses in `raw_response` column:
- Enables retroactive analysis
- Useful for tuning thresholds
- Required for compliance/auditing

Trade-off: Increases database storage.

### 7. Implement Caching (Future)

Consider caching fraud checks for repeated IPs:
- Reduces API costs
- Improves performance
- Cache for 1-5 minutes

**Example**:
```
User from 203.0.113.45 → Score: 15
(5 minutes later)
Same user from 203.0.113.45 → Use cached score
```

### 8. Tune Thresholds Regularly

Review fraud data monthly:

```sql
SELECT
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY risk_score) as median,
    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY risk_score) as p75,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY risk_score) as p95
FROM maxmind_minfraud_check
WHERE timestamp > NOW() - INTERVAL '30 days';
```

Adjust thresholds based on your user population.

### 9. Correlate with Other Signals

Combine minFraud scores with:
- Failed login attempts
- Password reset frequency
- Account age
- MFA enrollment status
- User behavior analytics

### 10. Plan for Maintenance Windows

MaxMind performs maintenance (rare):
- Check status: https://status.maxmind.com
- Use FAIL_OPEN during maintenance
- Have runbook for handling outages

## SDK Documentation

Full SDK documentation:
- JavaDoc: https://maxmind.github.io/minfraud-api-java/
- GitHub: https://github.com/maxmind/minfraud-api-java
- Examples: https://dev.maxmind.com/minfraud/evaluate-a-transaction

## API Documentation

Official MaxMind minFraud API documentation:
- Overview: https://dev.maxmind.com/minfraud
- API Reference: https://dev.maxmind.com/minfraud/api-documentation
- Request/Response: https://dev.maxmind.com/minfraud/api-documentation/requests
- Device Tracking: https://dev.maxmind.com/minfraud/track-devices

## Next Steps

- Review [CONFIGURATION.md](CONFIGURATION.md) for tuning risk actions
- See [DATABASE.md](DATABASE.md) for analyzing stored responses
- Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for API error resolution
