package com.zymlabs.keycloak.maxmindprovider;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Details;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.ws.rs.core.Response;

/**
 * Enforces MFA requirement when MaxMind fraud detection triggers CHALLENGE action.
 *
 * This authenticator:
 * 1. Checks if MaxMind set the "maxmind_challenge" auth note
 * 2. If no challenge, allows authentication to proceed
 * 3. If challenge exists, verifies user has at least one configured MFA credential
 * 4. Blocks users without MFA (prevents OTP setup during suspicious login)
 * 5. Allows users with MFA to proceed to MFA verification step
 *
 * Configuration:
 * - mfaCredentialTypes: Comma-separated list of credential types to check
 *   (default: "otp,webauthn")
 *   Supports: otp, webauthn, sms-otp (if extension installed), etc.
 *
 * Flow Structure:
 * Browser Forms
 * ├── Username Password Form (REQUIRED)
 * ├── MaxMind minFraud (REQUIRED) ← Sets maxmind_challenge auth note
 * ├── MaxMind MFA Enforcer (REQUIRED) ← This authenticator
 * └── Conditional OTP (CONDITIONAL) ← Only executes if user has MFA
 */
public class MaxMindMfaEnforcerAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(MaxMindMfaEnforcerAuthenticator.class);

    // Configuration keys
    public static final String CONFIG_MFA_CREDENTIAL_TYPES = "mfaCredentialTypes";

    // Default credential types to check
    private static final String DEFAULT_MFA_TYPES = "otp,webauthn";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        // Check if MaxMind set the challenge auth note
        String challengeNote = context.getAuthenticationSession().getAuthNote("maxmind_challenge");

        if (!"true".equals(challengeNote)) {
            // No challenge from MaxMind - skip MFA enforcement
            logger.debugf("No MaxMind challenge detected, allowing authentication to proceed");
            context.success();
            return;
        }

        // Challenge detected - check if user has MFA configured
        UserModel user = context.getUser();
        String riskScore = context.getAuthenticationSession().getAuthNote("maxmind_risk_score");

        logger.infof("MaxMind CHALLENGE triggered for user %s (risk_score=%s), verifying MFA configuration",
                     user.getUsername(), riskScore);

        // Get configured MFA credential types
        String mfaTypes = getMfaCredentialTypes(context);
        logger.debugf("Checking MFA credential types: %s", mfaTypes);

        // Check if user has ANY of the configured MFA types
        boolean hasMfa = false;
        String configuredType = null;

        for (String type : mfaTypes.split(",")) {
            String trimmedType = type.trim();
            if (user.credentialManager().isConfiguredFor(trimmedType)) {
                hasMfa = true;
                configuredType = trimmedType;
                break;
            }
        }

        if (hasMfa) {
            // User has MFA configured - allow them to proceed to MFA verification
            logger.infof("User %s has MFA configured (type=%s), allowing authentication to proceed",
                         user.getUsername(), configuredType);

            // Log event for successful MFA verification
            context.getEvent()
                    .detail("maxmind_mfa_enforcer_decision", "ALLOWED")
                    .detail("maxmind_mfa_enforcer_type", configuredType)
                    .detail("maxmind_mfa_enforcer_risk_score", riskScore != null ? riskScore : "unknown")
                    .detail(Details.AUTH_METHOD, "maxmind_mfa_enforcer");

            context.success();
        } else {
            // User does NOT have MFA configured - block authentication
            logger.errorf("User %s does NOT have MFA configured (risk_score=%s), blocking authentication",
                          user.getUsername(), riskScore);

            // Log event for MFA enforcement block
            context.getEvent()
                    .detail("maxmind_mfa_enforcer_decision", "BLOCKED")
                    .detail("maxmind_mfa_enforcer_reason", "No MFA configured")
                    .detail("maxmind_mfa_enforcer_risk_score", riskScore != null ? riskScore : "unknown")
                    .detail("maxmind_mfa_enforcer_checked_types", mfaTypes)
                    .detail(Details.AUTH_METHOD, "maxmind_mfa_enforcer");

            // Block authentication with error message
            Response challenge = context.form()
                    .setAttribute("riskScore", riskScore != null ? riskScore : "unknown")
                    .setError("mfaRequiredForSuspiciousActivity")
                    .createErrorPage(Response.Status.FORBIDDEN);

            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // This authenticator doesn't present forms, so action is not used
        // Call authenticate instead
        authenticate(context);
    }

    /**
     * Get configured MFA credential types from authenticator config.
     *
     * @param context Authentication flow context
     * @return Comma-separated list of credential types (default: "otp,webauthn")
     */
    private String getMfaCredentialTypes(AuthenticationFlowContext context) {
        AuthenticatorConfigModel config = context.getAuthenticatorConfig();
        if (config == null) {
            return DEFAULT_MFA_TYPES;
        }

        String mfaTypes = config.getConfig().get(CONFIG_MFA_CREDENTIAL_TYPES);
        if (mfaTypes == null || mfaTypes.trim().isEmpty()) {
            return DEFAULT_MFA_TYPES;
        }

        return mfaTypes;
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
     * Helper method for testing MFA credential type checking.
     *
     * @param user User to check
     * @param credentialTypes Comma-separated list of credential types
     * @return true if user has at least one of the credential types configured
     */
    static boolean hasAnyMfaConfigured(UserModel user, String credentialTypes) {
        for (String type : credentialTypes.split(",")) {
            if (user.credentialManager().isConfiguredFor(type.trim())) {
                return true;
            }
        }
        return false;
    }
}
