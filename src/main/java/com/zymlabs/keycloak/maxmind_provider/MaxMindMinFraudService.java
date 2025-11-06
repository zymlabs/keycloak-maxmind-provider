package com.zymlabs.keycloak.maxmind_provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxmind.minfraud.WebServiceClient;
import com.maxmind.minfraud.request.Device;
import com.maxmind.minfraud.request.Email;
import com.maxmind.minfraud.request.Event;
import com.maxmind.minfraud.request.Transaction;
import com.maxmind.minfraud.response.*;
import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Service wrapper for MaxMind minFraud API integration.
 *
 * This service handles all communication with the MaxMind minFraud API,
 * supporting Score, Insights, and Factors service levels.
 */
public class MaxMindMinFraudService {

    private static final Logger logger = Logger.getLogger(MaxMindMinFraudService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final WebServiceClient client;
    private final ServiceLevel serviceLevel;

    public enum ServiceLevel {
        SCORE,
        INSIGHTS,
        FACTORS
    }

    /**
     * Result object containing the fraud check response.
     */
    public static class FraudCheckResult {
        private final double riskScore;
        private final String requestId;
        private final String rawResponse;
        private final boolean success;
        private final String errorMessage;

        public FraudCheckResult(double riskScore, String requestId, String rawResponse) {
            this.riskScore = riskScore;
            this.requestId = requestId;
            this.rawResponse = rawResponse;
            this.success = true;
            this.errorMessage = null;
        }

        public FraudCheckResult(String errorMessage) {
            this.riskScore = -1.0;
            this.requestId = null;
            this.rawResponse = null;
            this.success = false;
            this.errorMessage = errorMessage;
        }

        public double getRiskScore() {
            return riskScore;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Constructor for MaxMindMinFraudService.
     *
     * @param accountId MaxMind account ID
     * @param licenseKey MaxMind license key
     * @param serviceLevel Service level (SCORE, INSIGHTS, or FACTORS)
     * @param connectTimeoutMs Connection timeout in milliseconds
     * @param readTimeoutMs Read timeout in milliseconds
     */
    public MaxMindMinFraudService(int accountId, String licenseKey, ServiceLevel serviceLevel,
                                  int connectTimeoutMs, int readTimeoutMs) {
        this.client = new WebServiceClient.Builder(accountId, licenseKey)
                .connectTimeout(connectTimeoutMs)
                .readTimeout(readTimeoutMs)
                .build();
        this.serviceLevel = serviceLevel;
        logger.infof("MaxMindMinFraudService initialized with service level: %s, " +
                     "connect timeout: %dms, read timeout: %dms",
                     serviceLevel, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Package-private constructor for testing with mocked WebServiceClient.
     *
     * @param client Mocked or test WebServiceClient
     * @param serviceLevel Service level (SCORE, INSIGHTS, or FACTORS)
     */
    MaxMindMinFraudService(WebServiceClient client, ServiceLevel serviceLevel) {
        this.client = client;
        this.serviceLevel = serviceLevel;
    }

    /**
     * Perform a fraud check.
     *
     * @param ipAddress User's IP address (required)
     * @param email User's email address (optional)
     * @param deviceSessionId MaxMind device tracking session ID (optional)
     * @return FraudCheckResult containing risk score and response data
     */
    public FraudCheckResult checkFraud(String ipAddress, String email, String deviceSessionId) {
        try {
            logger.debugf("Performing fraud check for IP: %s, Email: %s", ipAddress, email != null ? email : "none");

            // Build the transaction request
            Transaction.Builder transactionBuilder = new Transaction.Builder(
                    new Device.Builder(InetAddress.getByName(ipAddress))
                            .sessionId(deviceSessionId)
                            .build()
            );

            // Add email if provided
            if (email != null && !email.isEmpty()) {
                transactionBuilder.email(new Email.Builder().address(email).build());
            }

            // Add event details
            transactionBuilder.event(new Event.Builder()
                    .transactionId(java.util.UUID.randomUUID().toString())
                    .build());

            Transaction transaction = transactionBuilder.build();

            // Call appropriate API based on service level
            Object response;
            double riskScore;
            String requestId;

            switch (serviceLevel) {
                case SCORE:
                    ScoreResponse scoreResponse = client.score(transaction);
                    riskScore = scoreResponse.getRiskScore();
                    requestId = scoreResponse.getId().toString();
                    response = scoreResponse;
                    logger.debugf("Score API response: risk_score=%f, request_id=%s", riskScore, requestId);
                    break;

                case INSIGHTS:
                    InsightsResponse insightsResponse = client.insights(transaction);
                    riskScore = insightsResponse.getRiskScore();
                    requestId = insightsResponse.getId().toString();
                    response = insightsResponse;
                    logger.debugf("Insights API response: risk_score=%f, request_id=%s", riskScore, requestId);
                    break;

                case FACTORS:
                    FactorsResponse factorsResponse = client.factors(transaction);
                    riskScore = factorsResponse.getRiskScore();
                    requestId = factorsResponse.getId().toString();
                    response = factorsResponse;
                    logger.debugf("Factors API response: risk_score=%f, request_id=%s", riskScore, requestId);
                    break;

                default:
                    throw new IllegalStateException("Unsupported service level: " + serviceLevel);
            }

            // Serialize response to JSON
            String rawResponse = objectMapper.writeValueAsString(response);

            return new FraudCheckResult(riskScore, requestId, rawResponse);

        } catch (UnknownHostException e) {
            logger.errorf(e, "Invalid IP address: %s", ipAddress);
            return new FraudCheckResult("Invalid IP address: " + ipAddress);
        } catch (Exception e) {
            logger.errorf(e, "Error calling MaxMind minFraud API");
            return new FraudCheckResult("API error: " + e.getMessage());
        }
    }

    /**
     * Close the WebServiceClient and release resources.
     */
    public void close() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            logger.warnf(e, "Error closing MaxMind WebServiceClient");
        }
    }
}
