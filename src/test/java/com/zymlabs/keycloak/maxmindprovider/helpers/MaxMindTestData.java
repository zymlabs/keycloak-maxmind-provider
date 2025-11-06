package com.zymlabs.keycloak.maxmindprovider.helpers;

import com.maxmind.minfraud.response.*;
import java.util.UUID;

/**
 * Test data builder for MaxMind minFraud API responses.
 *
 * Provides mock response objects for testing without calling the actual API.
 */
public class MaxMindTestData {

    /**
     * Create a mock ScoreResponse with given risk score.
     */
    public static ScoreResponse createScoreResponse(double riskScore) {
        // Note: MaxMind response objects are typically immutable and created from JSON
        // For testing, we'll need to mock the responses rather than construct them directly
        // This class provides the expected structure for test documentation
        return null; // Will be mocked in tests
    }

    /**
     * Create a mock InsightsResponse with risk score and IP data.
     */
    public static InsightsResponse createInsightsResponse(double riskScore, boolean isAnonymous) {
        return null; // Will be mocked in tests
    }

    /**
     * Create a mock FactorsResponse with risk score and detailed factors.
     */
    public static FactorsResponse createFactorsResponse(double riskScore) {
        return null; // Will be mocked in tests
    }

    /**
     * Generate a random UUID for request IDs.
     */
    public static UUID randomRequestId() {
        return UUID.randomUUID();
    }

    /**
     * Common test IP addresses.
     */
    public static final String TEST_IP_CLEAN = "8.8.8.8";
    public static final String TEST_IP_VPN = "45.138.98.4";
    public static final String TEST_IP_TOR = "185.220.101.1";
    public static final String TEST_IP_INVALID = "999.999.999.999";
    public static final String TEST_IP_LOCAL = "127.0.0.1";

    /**
     * Common test email addresses.
     */
    public static final String TEST_EMAIL_CLEAN = "user@example.com";
    public static final String TEST_EMAIL_DISPOSABLE = "test@tempmail.com";
    public static final String TEST_EMAIL_HIGH_RISK = "fraud@suspicious.ru";

    /**
     * Common test device session IDs.
     */
    public static final String TEST_DEVICE_ID_CLEAN = "abc-123-def-456";
    public static final String TEST_DEVICE_ID_SUSPICIOUS = "xyz-789-bad-000";
}
