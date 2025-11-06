package com.zymlabs.keycloak.maxmind_provider;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Details;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/**
 * Keycloak Authenticator that performs fraud detection using MaxMind minFraud API.
 *
 * This authenticator intercepts the login flow, collects user data (IP, email, device fingerprint),
 * calls the MaxMind minFraud API, and takes action based on the risk score and configured thresholds.
 */
public class MaxMindMinFraudAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(MaxMindMinFraudAuthenticator.class);

    // Configuration keys
    public static final String CONFIG_ACCOUNT_ID = "accountId";
    public static final String CONFIG_LICENSE_KEY = "licenseKey";
    public static final String CONFIG_SERVICE_LEVEL = "serviceLevel";
    public static final String CONFIG_DEVICE_TRACKING_ENABLED = "deviceTrackingEnabled";
    public static final String CONFIG_LOW_RISK_THRESHOLD = "lowRiskThreshold";
    public static final String CONFIG_HIGH_RISK_THRESHOLD = "highRiskThreshold";
    public static final String CONFIG_LOW_RISK_ACTION = "lowRiskAction";
    public static final String CONFIG_MEDIUM_RISK_ACTION = "mediumRiskAction";
    public static final String CONFIG_HIGH_RISK_ACTION = "highRiskAction";
    public static final String CONFIG_FAIL_MODE = "failMode";
    public static final String CONFIG_CONNECT_TIMEOUT = "connectTimeout";
    public static final String CONFIG_READ_TIMEOUT = "readTimeout";
    public static final String CONFIG_RECORD_FRAUD_CHECKS = "recordFraudChecks";

    // Risk actions
    public enum RiskAction {
        ALLOW,
        CHALLENGE,
        BLOCK
    }

    // Fail modes
    public enum FailMode {
        FAIL_OPEN,  // Allow login if API fails
        FAIL_CLOSED // Block login if API fails
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Check if device tracking is enabled
        if (isDeviceTrackingEnabled(context)) {
            // Inject Device Tracking JavaScript and present form
            presentDeviceTrackingForm(context);
        } else {
            // No device tracking, perform fraud check immediately
            performFraudCheck(context, null);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // This is called when user submits the form (if device tracking is enabled)
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String deviceSessionId = formData.getFirst("deviceSessionId");

        logger.debugf("Received device session ID: %s", deviceSessionId);

        // Perform fraud check with device session ID
        performFraudCheck(context, deviceSessionId);
    }

    /**
     * Present a form with Device Tracking JavaScript injected.
     */
    private void presentDeviceTrackingForm(AuthenticationFlowContext context) {
        LoginFormsProvider form = context.form();

        // Inject MaxMind Device Tracking script
        String deviceTrackingScript =
            "<script src=\"https://device.maxmind.com/js/device.js\"></script>" +
            "<script>" +
            "  var deviceSessionId = null;" +
            "  if (typeof MaxMind !== 'undefined') {" +
            "    deviceSessionId = MaxMind.getSessionId();" +
            "    console.log('MaxMind Device Session ID:', deviceSessionId);" +
            "  }" +
            "  window.addEventListener('load', function() {" +
            "    var form = document.getElementById('kc-form-login');" +
            "    if (form && deviceSessionId) {" +
            "      var input = document.createElement('input');" +
            "      input.type = 'hidden';" +
            "      input.name = 'deviceSessionId';" +
            "      input.value = deviceSessionId;" +
            "      form.appendChild(input);" +
            "    }" +
            "  });" +
            "</script>";

        form.setAttribute("maxmindDeviceTracking", deviceTrackingScript);

        // Return success to continue with normal login flow
        // The device tracking script will be included in the page
        context.success();
    }

    /**
     * Perform the actual fraud check.
     */
    private void performFraudCheck(AuthenticationFlowContext context, String deviceSessionId) {
        UserModel user = context.getUser();
        String ipAddress = context.getConnection().getRemoteAddr();
        String email = user.getEmail();
        String userId = user.getId();
        String realmId = context.getRealm().getId();

        logger.infof("Performing fraud check for user %s (ID: %s) from IP: %s",
                     user.getUsername(), userId, ipAddress);

        // Get configuration
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            logger.error("MaxMind authenticator is not configured");
            handleConfigurationError(context);
            return;
        }

        Map<String, String> configMap = config.getConfig();

        // Parse configuration
        try {
            int accountId = Integer.parseInt(configMap.get(CONFIG_ACCOUNT_ID));
            String licenseKey = configMap.get(CONFIG_LICENSE_KEY);
            MaxMindMinFraudService.ServiceLevel serviceLevel =
                MaxMindMinFraudService.ServiceLevel.valueOf(configMap.getOrDefault(CONFIG_SERVICE_LEVEL, "SCORE"));

            int lowRiskThreshold = Integer.parseInt(configMap.getOrDefault(CONFIG_LOW_RISK_THRESHOLD, "5"));
            int highRiskThreshold = Integer.parseInt(configMap.getOrDefault(CONFIG_HIGH_RISK_THRESHOLD, "70"));

            RiskAction lowRiskAction = RiskAction.valueOf(configMap.getOrDefault(CONFIG_LOW_RISK_ACTION, "ALLOW"));
            RiskAction mediumRiskAction = RiskAction.valueOf(configMap.getOrDefault(CONFIG_MEDIUM_RISK_ACTION, "CHALLENGE"));
            RiskAction highRiskAction = RiskAction.valueOf(configMap.getOrDefault(CONFIG_HIGH_RISK_ACTION, "BLOCK"));

            FailMode failMode = FailMode.valueOf(configMap.getOrDefault(CONFIG_FAIL_MODE, "FAIL_OPEN"));

            // Parse timeout settings with defaults
            int connectTimeout = Integer.parseInt(configMap.getOrDefault(CONFIG_CONNECT_TIMEOUT, "3000"));
            int readTimeout = Integer.parseInt(configMap.getOrDefault(CONFIG_READ_TIMEOUT, "5000"));

            // Create MaxMind service
            MaxMindMinFraudService service = new MaxMindMinFraudService(accountId, licenseKey, serviceLevel,
                    connectTimeout, readTimeout);

            try {
                // Call MaxMind API
                MaxMindMinFraudService.FraudCheckResult result = service.checkFraud(ipAddress, email, deviceSessionId);

                if (result.isSuccess()) {
                    double riskScore = result.getRiskScore();
                    logger.infof("Fraud check completed: risk_score=%f, request_id=%s", riskScore, result.getRequestId());

                    // Determine risk level and action using extracted helper methods
                    String riskLevel = evaluateRiskLevel(riskScore, lowRiskThreshold, highRiskThreshold);
                    RiskAction action = determineRiskAction(riskLevel, lowRiskAction, mediumRiskAction, highRiskAction);

                    logger.infof("Risk level: %s (score=%f), Action: %s", riskLevel, riskScore, action);

                    // Log event for successful fraud check
                    context.getEvent()
                            .detail("maxmind_minfraud_risk_score", String.format("%.2f", riskScore))
                            .detail("maxmind_minfraud_risk_level", riskLevel)
                            .detail("maxmind_minfraud_decision", action.name())
                            .detail("maxmind_minfraud_request_id", result.getRequestId())
                            .detail("maxmind_minfraud_service_level", serviceLevel.name())
                            .detail(Details.AUTH_METHOD, "maxmind_minfraud");

                    // Store fraud check result
                    storeFraudCheck(context, userId, realmId, user.getUsername(), email, ipAddress,
                                  riskScore, action.name(), deviceSessionId, result.getRequestId(),
                                  result.getRawResponse(), serviceLevel.name(), null);

                    // Take action based on risk level
                    handleRiskAction(context, action, riskScore);

                } else {
                    // API call failed
                    logger.errorf("MaxMind API call failed: %s", result.getErrorMessage());

                    // Log event for API failure
                    context.getEvent()
                            .detail("maxmind_minfraud_error", result.getErrorMessage())
                            .detail("maxmind_minfraud_decision", "ERROR")
                            .detail("maxmind_minfraud_service_level", serviceLevel.name())
                            .detail(Details.AUTH_METHOD, "maxmind_minfraud");

                    // Store error
                    storeFraudCheck(context, userId, realmId, user.getUsername(), email, ipAddress,
                                  -1.0, "ERROR", deviceSessionId, null, null, serviceLevel.name(),
                                  result.getErrorMessage());

                    // Handle based on fail mode
                    if (failMode == FailMode.FAIL_CLOSED) {
                        logger.warn("Fail-closed mode: blocking login due to API error");
                        Response challenge = context.form()
                                .setError("maxmindApiError")
                                .createErrorPage(Response.Status.FORBIDDEN);
                        context.failure(AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR, challenge);
                    } else {
                        logger.warn("Fail-open mode: allowing login despite API error");
                        context.success();
                    }
                }
            } finally {
                service.close();
            }

        } catch (Exception e) {
            logger.errorf(e, "Error during fraud check");
            // Store error
            storeFraudCheck(context, userId, realmId, user.getUsername(), email, ipAddress,
                          -1.0, "ERROR", deviceSessionId, null, null, null,
                          "Configuration error: " + e.getMessage());
            handleConfigurationError(context);
        }
    }

    /**
     * Handle the risk-based action.
     */
    private void handleRiskAction(AuthenticationFlowContext context, RiskAction action, double riskScore) {
        switch (action) {
            case ALLOW:
                logger.infof("Risk action: ALLOW - Proceeding with authentication");
                context.success();
                break;

            case CHALLENGE:
                logger.warnf("Risk action: CHALLENGE - Requiring additional authentication (risk_score=%f)", riskScore);
                // Log event for CHALLENGE action
                context.getEvent()
                        .detail("maxmind_minfraud_action", "CHALLENGE")
                        .detail("maxmind_minfraud_risk_score", String.format("%.2f", riskScore));
                // Mark as attempted, which will force conditional flows (like OTP) to execute
                context.attempted();
                break;

            case BLOCK:
                logger.errorf("Risk action: BLOCK - Denying authentication (risk_score=%f)", riskScore);
                // Log event for BLOCK action
                context.getEvent()
                        .detail("maxmind_minfraud_action", "BLOCK")
                        .detail("maxmind_minfraud_risk_score", String.format("%.2f", riskScore));
                Response challenge = context.form()
                        .setAttribute("riskScore", String.format("%.2f", riskScore))
                        .setError("loginTooRisky")
                        .createErrorPage(Response.Status.FORBIDDEN);
                context.failure(AuthenticationFlowError.INVALID_USER, challenge);
                break;
        }
    }

    /**
     * Store fraud check result in database.
     */
    private void storeFraudCheck(AuthenticationFlowContext context, String userId, String realmId,
                                String username, String email, String ipAddress, double riskScore,
                                String decision, String deviceSessionId, String requestId,
                                String rawResponse, String serviceLevel, String errorMessage) {
        // Check if database recording is enabled
        if (!isRecordFraudChecksEnabled(context)) {
            logger.debugf("Database recording disabled, skipping fraud check storage");
            return;
        }

        try {
            // Use direct EntityManager access instead of custom provider
            jakarta.persistence.EntityManager em = context.getSession()
                    .getProvider(org.keycloak.connections.jpa.JpaConnectionProvider.class)
                    .getEntityManager();

            MaxMindMinFraudCheckEntity entity = new MaxMindMinFraudCheckEntity();
            entity.setUserId(userId);
            entity.setRealmId(realmId);
            entity.setUsername(username);
            entity.setEmail(email);
            entity.setIpAddress(ipAddress);
            entity.setRiskScore(riskScore);
            entity.setDecision(decision);
            entity.setDeviceSessionId(deviceSessionId);
            entity.setRequestId(requestId);
            entity.setRawResponse(rawResponse);
            entity.setServiceLevel(serviceLevel);
            entity.setErrorMessage(errorMessage);

            em.persist(entity);
            logger.debugf("Fraud check record stored: id=%d", entity.getId());
        } catch (Exception e) {
            logger.errorf(e, "Error storing fraud check result");
        }
    }

    /**
     * Check if device tracking is enabled.
     */
    private boolean isDeviceTrackingEnabled(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            return false;
        }
        String enabled = config.getConfig().get(CONFIG_DEVICE_TRACKING_ENABLED);
        return "true".equalsIgnoreCase(enabled);
    }

    /**
     * Check if fraud check recording to database is enabled.
     * Defaults to true for backward compatibility.
     */
    private boolean isRecordFraudChecksEnabled(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            return true; // Default to enabled if no config
        }
        String enabled = config.getConfig().get(CONFIG_RECORD_FRAUD_CHECKS);
        // Default to true unless explicitly set to false
        return !"false".equalsIgnoreCase(enabled);
    }

    /**
     * Handle configuration error.
     */
    private void handleConfigurationError(AuthenticationFlowContext context) {
        // Log event for configuration error
        context.getEvent()
                .detail("maxmind_minfraud_error", "Configuration error")
                .detail("maxmind_minfraud_decision", "ERROR")
                .detail(Details.AUTH_METHOD, "maxmind_minfraud");
        Response challenge = context.form()
                .setError("maxmindConfigError")
                .createErrorPage(Response.Status.INTERNAL_SERVER_ERROR);
        context.failure(AuthenticationFlowError.INTERNAL_ERROR, challenge);
    }

    @Override
    public boolean requiresUser() {
        return true; // This authenticator requires a user to be identified
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true; // Always enabled if configured in the authentication flow
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // No required actions needed
    }

    @Override
    public void close() {
        // Nothing to close
    }

    /**
     * Package-private helper methods for testing
     */

    /**
     * Evaluate risk level based on score and thresholds.
     *
     * @param riskScore Risk score from MaxMind (0-100)
     * @param lowRiskThreshold Maximum score for low risk
     * @param highRiskThreshold Minimum score for high risk
     * @return Risk level: "LOW", "MEDIUM", or "HIGH"
     */
    static String evaluateRiskLevel(double riskScore, int lowRiskThreshold, int highRiskThreshold) {
        if (riskScore <= lowRiskThreshold) {
            return "LOW";
        } else if (riskScore <= highRiskThreshold) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    /**
     * Determine which action to take based on risk level.
     *
     * @param riskLevel Risk level (LOW, MEDIUM, HIGH)
     * @param lowRiskAction Action for low risk
     * @param mediumRiskAction Action for medium risk
     * @param highRiskAction Action for high risk
     * @return The action to take
     */
    static RiskAction determineRiskAction(String riskLevel, RiskAction lowRiskAction,
                                         RiskAction mediumRiskAction, RiskAction highRiskAction) {
        return switch (riskLevel) {
            case "LOW" -> lowRiskAction;
            case "MEDIUM" -> mediumRiskAction;
            case "HIGH" -> highRiskAction;
            default -> throw new IllegalArgumentException("Invalid risk level: " + riskLevel);
        };
    }

    /**
     * Determine whether to allow or block login based on fail mode.
     *
     * @param failMode The configured fail mode
     * @return true if login should be allowed, false if blocked
     */
    static boolean shouldAllowOnFailure(FailMode failMode) {
        return failMode == FailMode.FAIL_OPEN;
    }
}
