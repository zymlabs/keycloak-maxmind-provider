package com.zymlabs.keycloak.maxmindprovider;

import com.zymlabs.keycloak.maxmindprovider.helpers.ConfigurationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static com.zymlabs.keycloak.maxmindprovider.MaxMindMinFraudAuthenticator.*;

/**
 * Tests for configuration parsing and validation.
 *
 * Tests parsing of configuration values from Keycloak admin console.
 */
@DisplayName("Configuration Parsing Tests")
class ConfigurationParsingTest {

    @Test
    @DisplayName("Valid configuration should parse all values correctly")
    void testValidConfiguration() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();

        // When/Then - verify all values are present
        assertThat(config.get(CONFIG_ACCOUNT_ID)).isEqualTo("123456");
        assertThat(config.get(CONFIG_LICENSE_KEY)).isEqualTo("test_license_key");
        assertThat(config.get(CONFIG_SERVICE_LEVEL)).isEqualTo("SCORE");
        assertThat(config.get(CONFIG_DEVICE_TRACKING_ENABLED)).isEqualTo("false");
        assertThat(config.get(CONFIG_LOW_RISK_THRESHOLD)).isEqualTo("30");
        assertThat(config.get(CONFIG_HIGH_RISK_THRESHOLD)).isEqualTo("70");
        assertThat(config.get(CONFIG_LOW_RISK_ACTION)).isEqualTo("ALLOW");
        assertThat(config.get(CONFIG_MEDIUM_RISK_ACTION)).isEqualTo("CHALLENGE");
        assertThat(config.get(CONFIG_HIGH_RISK_ACTION)).isEqualTo("BLOCK");
        assertThat(config.get(CONFIG_FAIL_MODE)).isEqualTo("FAIL_OPEN");
    }

    @Test
    @DisplayName("Parse account ID as integer")
    void testParseAccountId() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();

        // When
        int accountId = Integer.parseInt(config.get(CONFIG_ACCOUNT_ID));

        // Then
        assertThat(accountId).isEqualTo(123456);
    }

    @Test
    @DisplayName("Parse thresholds as integers")
    void testParseThresholds() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();

        // When
        int lowThreshold = Integer.parseInt(config.get(CONFIG_LOW_RISK_THRESHOLD));
        int highThreshold = Integer.parseInt(config.get(CONFIG_HIGH_RISK_THRESHOLD));

        // Then
        assertThat(lowThreshold).isEqualTo(30);
        assertThat(highThreshold).isEqualTo(70);
        assertThat(lowThreshold).isLessThan(highThreshold);
    }

    @Test
    @DisplayName("Parse service level enum")
    void testParseServiceLevel() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();

        // When
        MaxMindMinFraudService.ServiceLevel serviceLevel =
                MaxMindMinFraudService.ServiceLevel.valueOf(config.get(CONFIG_SERVICE_LEVEL));

        // Then
        assertThat(serviceLevel).isEqualTo(MaxMindMinFraudService.ServiceLevel.SCORE);
    }

    @Test
    @DisplayName("Parse INSIGHTS service level")
    void testParseInsightsServiceLevel() {
        // Given
        Map<String, String> config = ConfigurationTestData.configWithInsights();

        // When
        MaxMindMinFraudService.ServiceLevel serviceLevel =
                MaxMindMinFraudService.ServiceLevel.valueOf(config.get(CONFIG_SERVICE_LEVEL));

        // Then
        assertThat(serviceLevel).isEqualTo(MaxMindMinFraudService.ServiceLevel.INSIGHTS);
    }

    @Test
    @DisplayName("Parse FACTORS service level")
    void testParseFactorsServiceLevel() {
        // Given
        Map<String, String> config = ConfigurationTestData.configWithFactors();

        // When
        MaxMindMinFraudService.ServiceLevel serviceLevel =
                MaxMindMinFraudService.ServiceLevel.valueOf(config.get(CONFIG_SERVICE_LEVEL));

        // Then
        assertThat(serviceLevel).isEqualTo(MaxMindMinFraudService.ServiceLevel.FACTORS);
    }

    @Test
    @DisplayName("Parse risk actions enum")
    void testParseRiskActions() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();

        // When
        RiskAction lowAction = RiskAction.valueOf(config.get(CONFIG_LOW_RISK_ACTION));
        RiskAction mediumAction = RiskAction.valueOf(config.get(CONFIG_MEDIUM_RISK_ACTION));
        RiskAction highAction = RiskAction.valueOf(config.get(CONFIG_HIGH_RISK_ACTION));

        // Then
        assertThat(lowAction).isEqualTo(RiskAction.ALLOW);
        assertThat(mediumAction).isEqualTo(RiskAction.CHALLENGE);
        assertThat(highAction).isEqualTo(RiskAction.BLOCK);
    }

    @Test
    @DisplayName("Parse fail mode enum")
    void testParseFailMode() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();

        // When
        FailMode failMode = FailMode.valueOf(config.get(CONFIG_FAIL_MODE));

        // Then
        assertThat(failMode).isEqualTo(FailMode.FAIL_OPEN);
    }

    @Test
    @DisplayName("Parse FAIL_CLOSED mode")
    void testParseFailClosedMode() {
        // Given
        Map<String, String> config = ConfigurationTestData.configWithFailClosed();

        // When
        FailMode failMode = FailMode.valueOf(config.get(CONFIG_FAIL_MODE));

        // Then
        assertThat(failMode).isEqualTo(FailMode.FAIL_CLOSED);
    }

    @Test
    @DisplayName("Parse boolean string 'true' for device tracking")
    void testParseBooleanTrue() {
        // Given
        Map<String, String> config = ConfigurationTestData.configWithDeviceTracking();

        // When
        boolean deviceTrackingEnabled =
                Boolean.parseBoolean(config.get(CONFIG_DEVICE_TRACKING_ENABLED));

        // Then
        assertThat(deviceTrackingEnabled).isTrue();
    }

    @Test
    @DisplayName("Parse boolean string 'false' for device tracking")
    void testParseBooleanFalse() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();

        // When
        boolean deviceTrackingEnabled =
                Boolean.parseBoolean(config.get(CONFIG_DEVICE_TRACKING_ENABLED));

        // Then
        assertThat(deviceTrackingEnabled).isFalse();
    }

    @Test
    @DisplayName("Missing account ID should result in null")
    void testMissingAccountId() {
        // Given
        Map<String, String> config = ConfigurationTestData.configMissingAccountId();

        // When/Then
        assertThat(config.get(CONFIG_ACCOUNT_ID)).isNull();
    }

    @Test
    @DisplayName("Missing license key should result in null")
    void testMissingLicenseKey() {
        // Given
        Map<String, String> config = ConfigurationTestData.configMissingLicenseKey();

        // When/Then
        assertThat(config.get(CONFIG_LICENSE_KEY)).isNull();
    }

    @Test
    @DisplayName("Invalid service level should throw IllegalArgumentException")
    void testInvalidServiceLevel() {
        // Given
        Map<String, String> config = ConfigurationTestData.configInvalidServiceLevel();

        // When/Then
        assertThatThrownBy(() ->
                MaxMindMinFraudService.ServiceLevel.valueOf(config.get(CONFIG_SERVICE_LEVEL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Invalid threshold should throw NumberFormatException")
    void testInvalidThreshold() {
        // Given
        Map<String, String> config = ConfigurationTestData.configInvalidThreshold();

        // When/Then
        assertThatThrownBy(() ->
                Integer.parseInt(config.get(CONFIG_LOW_RISK_THRESHOLD)))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("Null threshold value should throw NumberFormatException")
    void testNullThreshold() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();
        config.remove(CONFIG_LOW_RISK_THRESHOLD);

        // When/Then
        assertThatThrownBy(() ->
                Integer.parseInt(config.get(CONFIG_LOW_RISK_THRESHOLD)))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("Strict thresholds should parse correctly")
    void testStrictThresholds() {
        // Given
        Map<String, String> config = ConfigurationTestData.configStrictThresholds();

        // When
        int lowThreshold = Integer.parseInt(config.get(CONFIG_LOW_RISK_THRESHOLD));
        int highThreshold = Integer.parseInt(config.get(CONFIG_HIGH_RISK_THRESHOLD));

        // Then
        assertThat(lowThreshold).isEqualTo(20);
        assertThat(highThreshold).isEqualTo(50);
    }

    @Test
    @DisplayName("Lenient thresholds should parse correctly")
    void testLenientThresholds() {
        // Given
        Map<String, String> config = ConfigurationTestData.configLenientThresholds();

        // When
        int lowThreshold = Integer.parseInt(config.get(CONFIG_LOW_RISK_THRESHOLD));
        int highThreshold = Integer.parseInt(config.get(CONFIG_HIGH_RISK_THRESHOLD));

        // Then
        assertThat(lowThreshold).isEqualTo(40);
        assertThat(highThreshold).isEqualTo(80);
    }

    @Test
    @DisplayName("Default value with getOrDefault should return default")
    void testDefaultValueHandling() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();
        config.remove(CONFIG_SERVICE_LEVEL);

        // When
        String serviceLevel = config.getOrDefault(CONFIG_SERVICE_LEVEL, "SCORE");

        // Then
        assertThat(serviceLevel).isEqualTo("SCORE");
    }

    @Test
    @DisplayName("Case-sensitive enum parsing should match exactly")
    void testCaseSensitiveEnumParsing() {
        // When/Then - lowercase should fail
        assertThatThrownBy(() -> RiskAction.valueOf("allow"))
                .isInstanceOf(IllegalArgumentException.class);

        // Uppercase should work
        assertThat(RiskAction.valueOf("ALLOW")).isEqualTo(RiskAction.ALLOW);
    }

    @Test
    @DisplayName("Empty string for boolean should parse as false")
    void testEmptyStringBoolean() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();
        config.put(CONFIG_DEVICE_TRACKING_ENABLED, "");

        // When
        boolean result = Boolean.parseBoolean(config.get(CONFIG_DEVICE_TRACKING_ENABLED));

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Whitespace in configuration values should be preserved")
    void testWhitespaceHandling() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();
        config.put(CONFIG_LICENSE_KEY, " key_with_spaces ");

        // When
        String licenseKey = config.get(CONFIG_LICENSE_KEY);

        // Then
        assertThat(licenseKey).isEqualTo(" key_with_spaces ");
        assertThat(licenseKey.trim()).isEqualTo("key_with_spaces");
    }

    @Test
    @DisplayName("Default timeout values should parse correctly")
    void testDefaultTimeouts() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();

        // When
        int connectTimeout = Integer.parseInt(config.get(CONFIG_CONNECT_TIMEOUT));
        int readTimeout = Integer.parseInt(config.get(CONFIG_READ_TIMEOUT));

        // Then
        assertThat(connectTimeout).isEqualTo(3000);
        assertThat(readTimeout).isEqualTo(5000);
    }

    @Test
    @DisplayName("Custom timeout values should parse correctly")
    void testCustomTimeouts() {
        // Given
        Map<String, String> config = ConfigurationTestData.configWithCustomTimeouts();

        // When
        int connectTimeout = Integer.parseInt(config.get(CONFIG_CONNECT_TIMEOUT));
        int readTimeout = Integer.parseInt(config.get(CONFIG_READ_TIMEOUT));

        // Then
        assertThat(connectTimeout).isEqualTo(5000);
        assertThat(readTimeout).isEqualTo(10000);
    }

    @Test
    @DisplayName("Short timeout values should parse correctly")
    void testShortTimeouts() {
        // Given
        Map<String, String> config = ConfigurationTestData.configShortTimeouts();

        // When
        int connectTimeout = Integer.parseInt(config.get(CONFIG_CONNECT_TIMEOUT));
        int readTimeout = Integer.parseInt(config.get(CONFIG_READ_TIMEOUT));

        // Then
        assertThat(connectTimeout).isEqualTo(1000);
        assertThat(readTimeout).isEqualTo(2000);
    }

    @Test
    @DisplayName("Invalid timeout should throw NumberFormatException")
    void testInvalidTimeout() {
        // Given
        Map<String, String> config = ConfigurationTestData.configInvalidTimeout();

        // When/Then
        assertThatThrownBy(() ->
                Integer.parseInt(config.get(CONFIG_CONNECT_TIMEOUT)))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("Missing timeout value should use default via getOrDefault")
    void testMissingTimeoutUsesDefault() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();
        config.remove(CONFIG_CONNECT_TIMEOUT);
        config.remove(CONFIG_READ_TIMEOUT);

        // When
        int connectTimeout = Integer.parseInt(config.getOrDefault(CONFIG_CONNECT_TIMEOUT, "3000"));
        int readTimeout = Integer.parseInt(config.getOrDefault(CONFIG_READ_TIMEOUT, "5000"));

        // Then
        assertThat(connectTimeout).isEqualTo(3000);
        assertThat(readTimeout).isEqualTo(5000);
    }

    @Test
    @DisplayName("Timeout values should be present in valid configuration")
    void testTimeoutsPresentInValidConfig() {
        // Given
        Map<String, String> config = ConfigurationTestData.validConfiguration();

        // When/Then
        assertThat(config.get(CONFIG_CONNECT_TIMEOUT)).isEqualTo("3000");
        assertThat(config.get(CONFIG_READ_TIMEOUT)).isEqualTo("5000");
    }
}
