package com.zymlabs.keycloak.maxmindprovider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MaxMindMinFraudCheckEntity.
 *
 * Verifies that the JPA entity correctly manages fraud check data including
 * getters/setters, default values, and serialization.
 */
@DisplayName("MaxMind Fraud Check Entity Tests")
class MaxMindMinFraudCheckEntityTest {

    private MaxMindMinFraudCheckEntity entity;

    @BeforeEach
    void setup() {
        entity = new MaxMindMinFraudCheckEntity();
    }

    @Test
    @DisplayName("Default constructor should initialize timestamp")
    void testDefaultConstructor_InitializesTimestamp() {
        // Given - entity created in setup()

        // Then
        assertThat(entity.getTimestamp())
                .as("Timestamp should be initialized automatically")
                .isNotNull()
                .as("Timestamp should be close to current time")
                .isCloseTo(new Date(), 1000);
    }

    @Test
    @DisplayName("Should set and get id")
    void testIdGetterSetter() {
        // Given
        Long id = 12345L;

        // When
        entity.setId(id);

        // Then
        assertThat(entity.getId())
                .as("getId() should return set value")
                .isEqualTo(id);
    }

    @Test
    @DisplayName("Should set and get userId")
    void testUserIdGetterSetter() {
        // Given
        String userId = "user-uuid-12345";

        // When
        entity.setUserId(userId);

        // Then
        assertThat(entity.getUserId())
                .as("getUserId() should return set value")
                .isEqualTo(userId);
    }

    @Test
    @DisplayName("Should set and get realmId")
    void testRealmIdGetterSetter() {
        // Given
        String realmId = "realm-uuid-12345";

        // When
        entity.setRealmId(realmId);

        // Then
        assertThat(entity.getRealmId())
                .as("getRealmId() should return set value")
                .isEqualTo(realmId);
    }

    @Test
    @DisplayName("Should set and get username")
    void testUsernameGetterSetter() {
        // Given
        String username = "testuser";

        // When
        entity.setUsername(username);

        // Then
        assertThat(entity.getUsername())
                .as("getUsername() should return set value")
                .isEqualTo(username);
    }

    @Test
    @DisplayName("Should set and get email")
    void testEmailGetterSetter() {
        // Given
        String email = "test@example.com";

        // When
        entity.setEmail(email);

        // Then
        assertThat(entity.getEmail())
                .as("getEmail() should return set value")
                .isEqualTo(email);
    }

    @Test
    @DisplayName("Should set and get ipAddress")
    void testIpAddressGetterSetter() {
        // Given
        String ipAddress = "192.168.1.100";

        // When
        entity.setIpAddress(ipAddress);

        // Then
        assertThat(entity.getIpAddress())
                .as("getIpAddress() should return set value")
                .isEqualTo(ipAddress);
    }

    @Test
    @DisplayName("Should set and get timestamp")
    void testTimestampGetterSetter() {
        // Given
        Date timestamp = new Date();

        // When
        entity.setTimestamp(timestamp);

        // Then
        assertThat(entity.getTimestamp())
                .as("getTimestamp() should return set value")
                .isEqualTo(timestamp);
    }

    @Test
    @DisplayName("Should set and get riskScore")
    void testRiskScoreGetterSetter() {
        // Given
        Double riskScore = 75.5;

        // When
        entity.setRiskScore(riskScore);

        // Then
        assertThat(entity.getRiskScore())
                .as("getRiskScore() should return set value")
                .isEqualTo(riskScore);
    }

    @Test
    @DisplayName("Should set and get decision")
    void testDecisionGetterSetter() {
        // Given
        String decision = "BLOCKED";

        // When
        entity.setDecision(decision);

        // Then
        assertThat(entity.getDecision())
                .as("getDecision() should return set value")
                .isEqualTo(decision);
    }

    @Test
    @DisplayName("Should set and get deviceSessionId")
    void testDeviceSessionIdGetterSetter() {
        // Given
        String deviceSessionId = "device-session-12345";

        // When
        entity.setDeviceSessionId(deviceSessionId);

        // Then
        assertThat(entity.getDeviceSessionId())
                .as("getDeviceSessionId() should return set value")
                .isEqualTo(deviceSessionId);
    }

    @Test
    @DisplayName("Should set and get requestId")
    void testRequestIdGetterSetter() {
        // Given
        String requestId = "request-uuid-12345";

        // When
        entity.setRequestId(requestId);

        // Then
        assertThat(entity.getRequestId())
                .as("getRequestId() should return set value")
                .isEqualTo(requestId);
    }

    @Test
    @DisplayName("Should set and get rawResponse")
    void testRawResponseGetterSetter() {
        // Given
        String rawResponse = "{\"risk_score\":75.5}";

        // When
        entity.setRawResponse(rawResponse);

        // Then
        assertThat(entity.getRawResponse())
                .as("getRawResponse() should return set value")
                .isEqualTo(rawResponse);
    }

    @Test
    @DisplayName("Should set and get serviceLevel")
    void testServiceLevelGetterSetter() {
        // Given
        String serviceLevel = "SCORE";

        // When
        entity.setServiceLevel(serviceLevel);

        // Then
        assertThat(entity.getServiceLevel())
                .as("getServiceLevel() should return set value")
                .isEqualTo(serviceLevel);
    }

    @Test
    @DisplayName("Should set and get errorMessage")
    void testErrorMessageGetterSetter() {
        // Given
        String errorMessage = "API error occurred";

        // When
        entity.setErrorMessage(errorMessage);

        // Then
        assertThat(entity.getErrorMessage())
                .as("getErrorMessage() should return set value")
                .isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("Should set and get eventId")
    void testEventIdGetterSetter() {
        // Given
        String eventId = "event-uuid-12345";

        // When
        entity.setEventId(eventId);

        // Then
        assertThat(entity.getEventId())
                .as("getEventId() should return set value")
                .isEqualTo(eventId);
    }

    @Test
    @DisplayName("toString() should include key fields")
    void testToString_IncludesKeyFields() {
        // Given
        entity.setId(123L);
        entity.setUserId("user-123");
        entity.setRealmId("realm-123");
        entity.setIpAddress("192.168.1.100");
        entity.setRiskScore(75.5);
        entity.setDecision("BLOCKED");

        // When
        String toString = entity.toString();

        // Then
        assertThat(toString)
                .as("toString() should include id")
                .contains("id=123")
                .as("toString() should include userId")
                .contains("userId='user-123'")
                .as("toString() should include realmId")
                .contains("realmId='realm-123'")
                .as("toString() should include ipAddress")
                .contains("ipAddress='192.168.1.100'")
                .as("toString() should include riskScore")
                .contains("riskScore=75.5")
                .as("toString() should include decision")
                .contains("decision='BLOCKED'");
    }

    @Test
    @DisplayName("toString() should not throw exception with null fields")
    void testToString_HandlesNullFields() {
        // When/Then - should not throw exception
        assertThatNoException()
                .as("toString() should handle null fields gracefully")
                .isThrownBy(() -> entity.toString());
    }

    @Test
    @DisplayName("Entity should be serializable")
    void testSerialization() throws Exception {
        // Given
        entity.setId(123L);
        entity.setUserId("user-123");
        entity.setRealmId("realm-123");
        entity.setUsername("testuser");
        entity.setEmail("test@example.com");
        entity.setIpAddress("192.168.1.100");
        entity.setRiskScore(75.5);
        entity.setDecision("BLOCKED");
        entity.setDeviceSessionId("device-123");
        entity.setRequestId("request-123");
        entity.setRawResponse("{\"risk\":75.5}");
        entity.setServiceLevel("SCORE");
        entity.setErrorMessage(null);
        entity.setEventId("event-123");

        // When - Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(entity);
        oos.close();

        // Then - Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        MaxMindMinFraudCheckEntity deserializedEntity = (MaxMindMinFraudCheckEntity) ois.readObject();
        ois.close();

        // Verify all fields
        assertThat(deserializedEntity.getId()).isEqualTo(123L);
        assertThat(deserializedEntity.getUserId()).isEqualTo("user-123");
        assertThat(deserializedEntity.getRealmId()).isEqualTo("realm-123");
        assertThat(deserializedEntity.getUsername()).isEqualTo("testuser");
        assertThat(deserializedEntity.getEmail()).isEqualTo("test@example.com");
        assertThat(deserializedEntity.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(deserializedEntity.getRiskScore()).isEqualTo(75.5);
        assertThat(deserializedEntity.getDecision()).isEqualTo("BLOCKED");
        assertThat(deserializedEntity.getDeviceSessionId()).isEqualTo("device-123");
        assertThat(deserializedEntity.getRequestId()).isEqualTo("request-123");
        assertThat(deserializedEntity.getRawResponse()).isEqualTo("{\"risk\":75.5}");
        assertThat(deserializedEntity.getServiceLevel()).isEqualTo("SCORE");
        assertThat(deserializedEntity.getErrorMessage()).isNull();
        assertThat(deserializedEntity.getEventId()).isEqualTo("event-123");
    }

    @Test
    @DisplayName("Entity should handle null values gracefully")
    void testNullValues() {
        // When
        entity.setUsername(null);
        entity.setEmail(null);
        entity.setDeviceSessionId(null);
        entity.setRequestId(null);
        entity.setRawResponse(null);
        entity.setServiceLevel(null);
        entity.setErrorMessage(null);
        entity.setEventId(null);

        // Then - should not throw exception
        assertThat(entity.getUsername()).isNull();
        assertThat(entity.getEmail()).isNull();
        assertThat(entity.getDeviceSessionId()).isNull();
        assertThat(entity.getRequestId()).isNull();
        assertThat(entity.getRawResponse()).isNull();
        assertThat(entity.getServiceLevel()).isNull();
        assertThat(entity.getErrorMessage()).isNull();
        assertThat(entity.getEventId()).isNull();
    }

    @Test
    @DisplayName("Should support all valid decision values")
    void testDecisionValues() {
        // When/Then
        entity.setDecision("ALLOWED");
        assertThat(entity.getDecision()).isEqualTo("ALLOWED");

        entity.setDecision("CHALLENGED");
        assertThat(entity.getDecision()).isEqualTo("CHALLENGED");

        entity.setDecision("BLOCKED");
        assertThat(entity.getDecision()).isEqualTo("BLOCKED");

        entity.setDecision("ERROR");
        assertThat(entity.getDecision()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("Should support all valid service level values")
    void testServiceLevelValues() {
        // When/Then
        entity.setServiceLevel("SCORE");
        assertThat(entity.getServiceLevel()).isEqualTo("SCORE");

        entity.setServiceLevel("INSIGHTS");
        assertThat(entity.getServiceLevel()).isEqualTo("INSIGHTS");

        entity.setServiceLevel("FACTORS");
        assertThat(entity.getServiceLevel()).isEqualTo("FACTORS");
    }

    @Test
    @DisplayName("Should support IPv4 and IPv6 addresses")
    void testIpAddressFormats() {
        // When/Then - IPv4
        entity.setIpAddress("192.168.1.100");
        assertThat(entity.getIpAddress()).isEqualTo("192.168.1.100");

        // When/Then - IPv6
        entity.setIpAddress("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertThat(entity.getIpAddress()).isEqualTo("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
    }

    @Test
    @DisplayName("Should support risk score range")
    void testRiskScoreRange() {
        // When/Then - Min risk score
        entity.setRiskScore(0.0);
        assertThat(entity.getRiskScore()).isEqualTo(0.0);

        // When/Then - Max risk score
        entity.setRiskScore(100.0);
        assertThat(entity.getRiskScore()).isEqualTo(100.0);

        // When/Then - Error indicator
        entity.setRiskScore(-1.0);
        assertThat(entity.getRiskScore()).isEqualTo(-1.0);

        // When/Then - Mid-range
        entity.setRiskScore(45.67);
        assertThat(entity.getRiskScore()).isEqualTo(45.67);
    }

    @Test
    @DisplayName("Should support long raw response text")
    void testLongRawResponse() {
        // Given - Large JSON response
        StringBuilder largeResponse = new StringBuilder("{");
        for (int i = 0; i < 1000; i++) {
            largeResponse.append("\"field").append(i).append("\":\"value").append(i).append("\",");
        }
        largeResponse.append("\"risk_score\":75.5}");

        // When
        entity.setRawResponse(largeResponse.toString());

        // Then
        assertThat(entity.getRawResponse())
                .as("Should handle large raw response")
                .isEqualTo(largeResponse.toString())
                .hasSize(largeResponse.length());
    }

    @Test
    @DisplayName("Timestamp should be mutable")
    void testTimestampMutability() {
        // Given
        Date originalTimestamp = entity.getTimestamp();
        Date newTimestamp = new Date(System.currentTimeMillis() - 86400000); // Yesterday

        // When
        entity.setTimestamp(newTimestamp);

        // Then
        assertThat(entity.getTimestamp())
                .as("Timestamp should be updated")
                .isEqualTo(newTimestamp)
                .as("Timestamp should be different from original")
                .isNotEqualTo(originalTimestamp);
    }
}
