package com.zymlabs.keycloak.maxmindprovider;

import com.maxmind.minfraud.WebServiceClient;
import com.maxmind.minfraud.request.Device;
import com.maxmind.minfraud.request.Email;
import com.maxmind.minfraud.request.Event;
import com.maxmind.minfraud.request.Transaction;
import com.maxmind.minfraud.response.*;
import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.UnknownHostException;

/**
 * Service wrapper for MaxMind minFraud API integration.
 *
 * This service handles all communication with the MaxMind minFraud API,
 * supporting Score, Insights, and Factors service levels.
 */
public class MaxMindMinFraudService {

    private static final Logger logger = Logger.getLogger(MaxMindMinFraudService.class);

    /**
     * One client per distinct configuration. The SDK client wraps a JDK HttpClient, which can't be
     * closed on Java 17 and holds a selector thread until garbage collected, so it is reused across
     * logins instead of being created per request. Entries are only added when an authenticator
     * config changes, so the map stays tiny.
     */
    private static final Map<ClientConfig, WebServiceClient> CLIENTS = new ConcurrentHashMap<>();

    record ClientConfig(int accountId, String licenseKey, ApiEndpoint endpoint,
                        int connectTimeoutMs, int readTimeoutMs) {
        @Override
        public String toString() {
            // Never log the license key
            return "ClientConfig[accountId=" + accountId + ", endpoint=" + endpoint +
                   ", connectTimeoutMs=" + connectTimeoutMs + ", readTimeoutMs=" + readTimeoutMs + "]";
        }
    }

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
     * Where to send minFraud requests. Parsed from the optional API host setting.
     */
    record ApiEndpoint(String host, int port, boolean https) {

        static final String DEFAULT_HOST = "minfraud.maxmind.com";

        /**
         * Parses {@code host}, {@code host:port}, or a URL such as {@code http://stub:8081}.
         * Blank means the MaxMind default. Only the scheme, host and port are used.
         *
         * @throws IllegalArgumentException if the value is not a valid host or http(s) URL
         */
        static ApiEndpoint parse(String value) {
            if (value == null || value.isBlank()) {
                return new ApiEndpoint(DEFAULT_HOST, -1, true);
            }
            String trimmed = value.trim();
            URI uri;
            try {
                uri = new URI(trimmed.contains("://") ? trimmed : "https://" + trimmed);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid MaxMind API host: " + value, e);
            }
            String scheme = uri.getScheme().toLowerCase();
            if (!scheme.equals("https") && !scheme.equals("http")) {
                throw new IllegalArgumentException("MaxMind API host must use http or https: " + value);
            }
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("Invalid MaxMind API host: " + value);
            }
            return new ApiEndpoint(uri.getHost(), uri.getPort(), scheme.equals("https"));
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
        this(accountId, licenseKey, serviceLevel, connectTimeoutMs, readTimeoutMs, null);
    }

    /**
     * Constructor for MaxMindMinFraudService with a custom API host.
     *
     * @param apiHost minFraud host, e.g. {@code sandbox.maxmind.com}; null or blank for the default
     *                ({@code minfraud.maxmind.com}). See {@link ApiEndpoint#parse(String)}.
     */
    public MaxMindMinFraudService(int accountId, String licenseKey, ServiceLevel serviceLevel,
                                  int connectTimeoutMs, int readTimeoutMs, String apiHost) {
        this(clientFor(new ClientConfig(accountId, licenseKey, ApiEndpoint.parse(apiHost),
                                        connectTimeoutMs, readTimeoutMs)), serviceLevel);
    }

    /**
     * Returns the shared client for a configuration, creating it on first use.
     */
    static WebServiceClient clientFor(ClientConfig config) {
        return CLIENTS.computeIfAbsent(config, MaxMindMinFraudService::buildClient);
    }

    private static WebServiceClient buildClient(ClientConfig config) {
        ApiEndpoint endpoint = config.endpoint();
        WebServiceClient.Builder builder = new WebServiceClient.Builder(config.accountId(), config.licenseKey())
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
                // The SDK's request timeout covers the whole response, like the old read timeout
                .requestTimeout(Duration.ofMillis(config.readTimeoutMs()))
                .host(endpoint.host());
        if (endpoint.port() > 0) {
            builder.port(endpoint.port());
        }
        if (!endpoint.https()) {
            logger.warnf("MaxMind API host %s uses plain HTTP; the license key is sent unencrypted. " +
                         "Only use this for local testing.", endpoint.host());
            builder.disableHttps();
        }
        logger.infof("Created MaxMind minFraud client for account %d, host: %s, " +
                     "connect timeout: %dms, read timeout: %dms",
                     config.accountId(), endpoint.host(), config.connectTimeoutMs(), config.readTimeoutMs());
        return builder.build();
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
     * @param userAgent User-Agent header from HTTP request (optional)
     * @param acceptLanguage Accept-Language header from HTTP request (optional)
     * @param keycloakSessionId Keycloak authentication session ID for transaction tracking (optional)
     * @return FraudCheckResult containing risk score and response data
     */
    public FraudCheckResult checkFraud(String ipAddress, String email, String deviceSessionId,
                                       String userAgent, String acceptLanguage, String keycloakSessionId) {
        try {
            logger.debugf("Performing fraud check for IP: %s, Email: %s", ipAddress, email != null ? email : "none");

            // Build the device object
            Device.Builder deviceBuilder = new Device.Builder(InetAddress.getByName(ipAddress));

            if (deviceSessionId != null && !deviceSessionId.isEmpty()) {
                deviceBuilder.sessionId(deviceSessionId);
            }

            if (userAgent != null && !userAgent.isEmpty()) {
                deviceBuilder.userAgent(userAgent);
                logger.debugf("Added User-Agent to request: %s", userAgent);
            }

            if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
                deviceBuilder.acceptLanguage(acceptLanguage);
                logger.debugf("Added Accept-Language to request: %s", acceptLanguage);
            }

            // Build the transaction request
            Transaction.Builder transactionBuilder = new Transaction.Builder(deviceBuilder.build());

            // Add email if provided
            if (email != null && !email.isEmpty()) {
                transactionBuilder.email(new Email.Builder().address(email).build());
            }

            // Add event details
            Event.Builder eventBuilder = new Event.Builder()
                    .type(Event.Type.ACCOUNT_LOGIN);

            // Use Keycloak session ID as transaction ID for correlation, fallback to UUID
            if (keycloakSessionId != null && !keycloakSessionId.isEmpty()) {
                eventBuilder.transactionId(keycloakSessionId);
                logger.debugf("Using Keycloak session ID as transaction ID: %s", keycloakSessionId);
            } else {
                eventBuilder.transactionId(java.util.UUID.randomUUID().toString());
                logger.debugf("No Keycloak session ID provided, using random UUID as transaction ID");
            }

            transactionBuilder.event(eventBuilder.build());

            Transaction transaction = transactionBuilder.build();

            // Call appropriate API based on service level
            String rawResponse;
            double riskScore;
            String requestId;

            switch (serviceLevel) {
                case SCORE:
                    ScoreResponse scoreResponse = client.score(transaction);
                    riskScore = scoreResponse.riskScore();
                    requestId = scoreResponse.id().toString();
                    rawResponse = scoreResponse.toJson();
                    logger.debugf("Score API response: risk_score=%f, request_id=%s", riskScore, requestId);
                    break;

                case INSIGHTS:
                    InsightsResponse insightsResponse = client.insights(transaction);
                    riskScore = insightsResponse.riskScore();
                    requestId = insightsResponse.id().toString();
                    rawResponse = insightsResponse.toJson();
                    logger.debugf("Insights API response: risk_score=%f, request_id=%s", riskScore, requestId);
                    break;

                case FACTORS:
                    FactorsResponse factorsResponse = client.factors(transaction);
                    riskScore = factorsResponse.riskScore();
                    requestId = factorsResponse.id().toString();
                    rawResponse = factorsResponse.toJson();
                    logger.debugf("Factors API response: risk_score=%f, request_id=%s", riskScore, requestId);
                    break;

                default:
                    throw new IllegalStateException("Unsupported service level: " + serviceLevel);
            }

            return new FraudCheckResult(riskScore, requestId, rawResponse);

        } catch (UnknownHostException e) {
            logger.errorf(e, "Invalid IP address: %s", ipAddress);
            return new FraudCheckResult("Invalid IP address: " + ipAddress);
        } catch (Exception e) {
            logger.errorf(e, "Error calling MaxMind minFraud API");
            return new FraudCheckResult("API error: " + e.getMessage());
        }
    }
}
