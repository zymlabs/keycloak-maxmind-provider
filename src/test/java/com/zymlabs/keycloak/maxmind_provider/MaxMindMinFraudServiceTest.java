package com.zymlabs.keycloak.maxmind_provider;

import com.maxmind.minfraud.WebServiceClient;
import com.maxmind.minfraud.exception.HttpException;
import com.maxmind.minfraud.exception.MinFraudException;
import com.maxmind.minfraud.request.Transaction;
import com.maxmind.minfraud.response.FactorsResponse;
import com.maxmind.minfraud.response.InsightsResponse;
import com.maxmind.minfraud.response.ScoreResponse;
import com.zymlabs.keycloak.maxmind_provider.helpers.MaxMindTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for MaxMindMinFraudService.
 *
 * Tests API integration with mocked WebServiceClient.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MaxMind Service Tests")
class MaxMindMinFraudServiceTest {

    @Mock
    private WebServiceClient mockClient;

    @Mock
    private ScoreResponse mockScoreResponse;

    @Mock
    private InsightsResponse mockInsightsResponse;

    @Mock
    private FactorsResponse mockFactorsResponse;

    private static final int ACCOUNT_ID = 123456;
    private static final String LICENSE_KEY = "test_license_key";

    @BeforeEach
    void setup() {
        // Setup common mock responses
        when(mockScoreResponse.getRiskScore()).thenReturn(15.5);
        when(mockScoreResponse.getId()).thenReturn(UUID.randomUUID());

        when(mockInsightsResponse.getRiskScore()).thenReturn(45.0);
        when(mockInsightsResponse.getId()).thenReturn(UUID.randomUUID());

        when(mockFactorsResponse.getRiskScore()).thenReturn(75.0);
        when(mockFactorsResponse.getId()).thenReturn(UUID.randomUUID());
    }

    @Test
    @DisplayName("Check fraud with SCORE level should return risk score")
    void testCheckFraud_ScoreLevel() throws Exception {
        // Given
        when(mockClient.score(any(Transaction.class))).thenReturn(mockScoreResponse);
        MaxMindMinFraudService service = new MaxMindMinFraudService(mockClient,
                MaxMindMinFraudService.ServiceLevel.SCORE);

        // When
        MaxMindMinFraudService.FraudCheckResult result =
                service.checkFraud(MaxMindTestData.TEST_IP_CLEAN, null, null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(15.5);
        assertThat(result.getRequestId()).isNotNull();
        assertThat(result.getRawResponse()).isNotNull();
        verify(mockClient).score(any(Transaction.class));
        verify(mockClient, never()).insights(any());
        verify(mockClient, never()).factors(any());
    }

    @Test
    @DisplayName("Check fraud with INSIGHTS level should call insights API")
    void testCheckFraud_InsightsLevel() throws Exception {
        // Given
        when(mockClient.insights(any(Transaction.class))).thenReturn(mockInsightsResponse);
        MaxMindMinFraudService service = new MaxMindMinFraudService(mockClient,
                MaxMindMinFraudService.ServiceLevel.INSIGHTS);

        // When
        MaxMindMinFraudService.FraudCheckResult result =
                service.checkFraud(MaxMindTestData.TEST_IP_CLEAN, null, null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(45.0);
        verify(mockClient).insights(any(Transaction.class));
        verify(mockClient, never()).score(any());
        verify(mockClient, never()).factors(any());
    }

    @Test
    @DisplayName("Check fraud with FACTORS level should call factors API")
    void testCheckFraud_FactorsLevel() throws Exception {
        // Given
        when(mockClient.factors(any(Transaction.class))).thenReturn(mockFactorsResponse);
        MaxMindMinFraudService service = new MaxMindMinFraudService(mockClient,
                MaxMindMinFraudService.ServiceLevel.FACTORS);

        // When
        MaxMindMinFraudService.FraudCheckResult result =
                service.checkFraud(MaxMindTestData.TEST_IP_CLEAN, null, null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(75.0);
        verify(mockClient).factors(any(Transaction.class));
        verify(mockClient, never()).score(any());
        verify(mockClient, never()).insights(any());
    }

    @Test
    @DisplayName("Check fraud with email should include email in request")
    void testCheckFraud_WithEmail() throws Exception {
        // Given
        when(mockClient.score(any(Transaction.class))).thenReturn(mockScoreResponse);
        MaxMindMinFraudService service = new MaxMindMinFraudService(mockClient,
                MaxMindMinFraudService.ServiceLevel.SCORE);

        // When
        MaxMindMinFraudService.FraudCheckResult result =
                service.checkFraud(MaxMindTestData.TEST_IP_CLEAN,
                        MaxMindTestData.TEST_EMAIL_CLEAN, null);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(mockClient).score(any(Transaction.class));
    }

    @Test
    @DisplayName("Check fraud with device session ID should include it in request")
    void testCheckFraud_WithDeviceSessionId() throws Exception {
        // Given
        when(mockClient.score(any(Transaction.class))).thenReturn(mockScoreResponse);
        MaxMindMinFraudService service = new MaxMindMinFraudService(mockClient,
                MaxMindMinFraudService.ServiceLevel.SCORE);

        // When
        MaxMindMinFraudService.FraudCheckResult result =
                service.checkFraud(MaxMindTestData.TEST_IP_CLEAN, null,
                        MaxMindTestData.TEST_DEVICE_ID_CLEAN);

        // Then
        assertThat(result.isSuccess()).isTrue();
        verify(mockClient).score(any(Transaction.class));
    }

    @Test
    @DisplayName("Check fraud with invalid IP should return error result")
    void testCheckFraud_InvalidIP() {
        // Given
        MaxMindMinFraudService service = new MaxMindMinFraudService(mockClient,
                MaxMindMinFraudService.ServiceLevel.SCORE);

        // When
        MaxMindMinFraudService.FraudCheckResult result =
                service.checkFraud(MaxMindTestData.TEST_IP_INVALID, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid IP address");
        assertThat(result.getRiskScore()).isEqualTo(-1.0);
    }

    @Test
    @DisplayName("Check fraud with API HttpException should return error result")
    void testCheckFraud_HttpException() throws Exception {
        // Given
        when(mockClient.score(any(Transaction.class)))
                .thenThrow(new HttpException("Insufficient funds", 402, null));
        MaxMindMinFraudService service = new MaxMindMinFraudService(mockClient,
                MaxMindMinFraudService.ServiceLevel.SCORE);

        // When
        MaxMindMinFraudService.FraudCheckResult result =
                service.checkFraud(MaxMindTestData.TEST_IP_CLEAN, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("API error");
    }

    @Test
    @DisplayName("Check fraud with generic exception should return error result")
    void testCheckFraud_GenericException() throws Exception {
        // Given
        when(mockClient.score(any(Transaction.class)))
                .thenThrow(new IOException("Network timeout"));
        MaxMindMinFraudService service = new MaxMindMinFraudService(mockClient,
                MaxMindMinFraudService.ServiceLevel.SCORE);

        // When
        MaxMindMinFraudService.FraudCheckResult result =
                service.checkFraud(MaxMindTestData.TEST_IP_CLEAN, null, null);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("API error");
    }

    @Test
    @DisplayName("Service close should not throw exception")
    void testServiceClose() {
        // Given
        MaxMindMinFraudService service = new MaxMindMinFraudService(mockClient,
                MaxMindMinFraudService.ServiceLevel.SCORE);

        // When/Then
        assertThatCode(service::close).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FraudCheckResult with success should have valid data")
    void testFraudCheckResult_Success() {
        // Given/When
        MaxMindMinFraudService.FraudCheckResult result =
                new MaxMindMinFraudService.FraudCheckResult(42.5, "req-123", "{\"score\": 42.5}");

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRiskScore()).isEqualTo(42.5);
        assertThat(result.getRequestId()).isEqualTo("req-123");
        assertThat(result.getRawResponse()).isEqualTo("{\"score\": 42.5}");
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("FraudCheckResult with error should have error message")
    void testFraudCheckResult_Error() {
        // Given/When
        MaxMindMinFraudService.FraudCheckResult result =
                new MaxMindMinFraudService.FraudCheckResult("Connection timeout");

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getRiskScore()).isEqualTo(-1.0);
        assertThat(result.getRequestId()).isNull();
        assertThat(result.getRawResponse()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Connection timeout");
    }
}
