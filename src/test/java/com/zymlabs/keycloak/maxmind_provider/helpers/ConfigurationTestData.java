package com.zymlabs.keycloak.maxmind_provider.helpers;

import com.zymlabs.keycloak.maxmind_provider.MaxMindMinFraudAuthenticator;

import java.util.HashMap;
import java.util.Map;

/**
 * Test data builder for authenticator configuration.
 *
 * Provides sample configuration maps for testing configuration parsing.
 */
public class ConfigurationTestData {

    /**
     * Create a valid, complete configuration.
     */
    public static Map<String, String> validConfiguration() {
        Map<String, String> config = new HashMap<>();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_ACCOUNT_ID, "123456");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_LICENSE_KEY, "test_license_key");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_SERVICE_LEVEL, "SCORE");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_DEVICE_TRACKING_ENABLED, "false");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_LOW_RISK_THRESHOLD, "30");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_HIGH_RISK_THRESHOLD, "70");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_LOW_RISK_ACTION, "ALLOW");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_MEDIUM_RISK_ACTION, "CHALLENGE");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_HIGH_RISK_ACTION, "BLOCK");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_FAIL_MODE, "FAIL_OPEN");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_CONNECT_TIMEOUT, "3000");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_READ_TIMEOUT, "5000");
        return config;
    }

    /**
     * Create configuration with missing account ID.
     */
    public static Map<String, String> configMissingAccountId() {
        Map<String, String> config = validConfiguration();
        config.remove(MaxMindMinFraudAuthenticator.CONFIG_ACCOUNT_ID);
        return config;
    }

    /**
     * Create configuration with missing license key.
     */
    public static Map<String, String> configMissingLicenseKey() {
        Map<String, String> config = validConfiguration();
        config.remove(MaxMindMinFraudAuthenticator.CONFIG_LICENSE_KEY);
        return config;
    }

    /**
     * Create configuration with invalid service level.
     */
    public static Map<String, String> configInvalidServiceLevel() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_SERVICE_LEVEL, "INVALID");
        return config;
    }

    /**
     * Create configuration with invalid threshold (non-numeric).
     */
    public static Map<String, String> configInvalidThreshold() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_LOW_RISK_THRESHOLD, "not_a_number");
        return config;
    }

    /**
     * Create configuration with device tracking enabled.
     */
    public static Map<String, String> configWithDeviceTracking() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_DEVICE_TRACKING_ENABLED, "true");
        return config;
    }

    /**
     * Create configuration with INSIGHTS service level.
     */
    public static Map<String, String> configWithInsights() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_SERVICE_LEVEL, "INSIGHTS");
        return config;
    }

    /**
     * Create configuration with FACTORS service level.
     */
    public static Map<String, String> configWithFactors() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_SERVICE_LEVEL, "FACTORS");
        return config;
    }

    /**
     * Create configuration with FAIL_CLOSED mode.
     */
    public static Map<String, String> configWithFailClosed() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_FAIL_MODE, "FAIL_CLOSED");
        return config;
    }

    /**
     * Create configuration with strict thresholds.
     */
    public static Map<String, String> configStrictThresholds() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_LOW_RISK_THRESHOLD, "20");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_HIGH_RISK_THRESHOLD, "50");
        return config;
    }

    /**
     * Create configuration with lenient thresholds.
     */
    public static Map<String, String> configLenientThresholds() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_LOW_RISK_THRESHOLD, "40");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_HIGH_RISK_THRESHOLD, "80");
        return config;
    }

    /**
     * Create configuration with custom timeouts.
     */
    public static Map<String, String> configWithCustomTimeouts() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_CONNECT_TIMEOUT, "5000");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_READ_TIMEOUT, "10000");
        return config;
    }

    /**
     * Create configuration with invalid timeout (non-numeric).
     */
    public static Map<String, String> configInvalidTimeout() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_CONNECT_TIMEOUT, "not_a_number");
        return config;
    }

    /**
     * Create configuration with very short timeouts.
     */
    public static Map<String, String> configShortTimeouts() {
        Map<String, String> config = validConfiguration();
        config.put(MaxMindMinFraudAuthenticator.CONFIG_CONNECT_TIMEOUT, "1000");
        config.put(MaxMindMinFraudAuthenticator.CONFIG_READ_TIMEOUT, "2000");
        return config;
    }
}
