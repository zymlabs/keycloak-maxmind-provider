package com.zymlabs.keycloak.maxmindprovider;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory for creating MaxMindMinFraudAuthenticator instances.
 *
 * This factory provides the configuration UI in Keycloak admin console
 * and creates authenticator instances for the authentication flow.
 */
public class MaxMindMinFraudAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "maxmind-minfraud-authenticator";
    private static final String DISPLAY_NAME = "MaxMind minFraud";
    private static final String HELP_TEXT = "Performs fraud detection using MaxMind minFraud API during login";

    private static final MaxMindMinFraudAuthenticator SINGLETON = new MaxMindMinFraudAuthenticator();

    @Override
    public String getDisplayType() {
        return DISPLAY_NAME;
    }

    @Override
    public String getReferenceCategory() {
        return "fraud-detection";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.CONDITIONAL,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return HELP_TEXT;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()

                // MaxMind API Credentials
                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_ACCOUNT_ID)
                    .label("MaxMind Account ID")
                    .helpText("Your MaxMind account ID")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .add()

                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_LICENSE_KEY)
                    .label("MaxMind License Key")
                    .helpText("Your MaxMind license key")
                    .type(ProviderConfigProperty.PASSWORD)
                    .secret(true)
                    .add()

                // Service Level
                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_SERVICE_LEVEL)
                    .label("Service Level")
                    .helpText("MaxMind minFraud service level (SCORE, INSIGHTS, or FACTORS)")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .options("SCORE", "INSIGHTS", "FACTORS")
                    .defaultValue("SCORE")
                    .add()

                // Device Tracking
                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_DEVICE_TRACKING_ENABLED)
                    .label("Enable Device Tracking")
                    .helpText("Enable MaxMind Device Tracking for device fingerprinting")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue("false")
                    .add()

                // Database Recording
                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_RECORD_FRAUD_CHECKS)
                    .label("Record Fraud Checks to Database")
                    .helpText("Enable storage of fraud check results in the maxmind_minfraud_check database table for analytics and auditing. Events are still logged regardless of this setting.")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue("true")
                    .add()

                // IP Filtering
                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_IP_ALLOWLIST)
                    .label("IP Allowlist")
                    .helpText("Comma-separated list of IPs/CIDRs to always allow, bypassing fraud detection. Takes precedence over blocklist. Supports IPv4 and IPv6. Default includes standard private/internal ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8, ::1/128, fc00::/7, fe80::/10).")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8,::1/128,fc00::/7,fe80::/10")
                    .add()

                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_IP_BLOCKLIST)
                    .label("IP Blocklist")
                    .helpText("Comma-separated list of IPs/CIDRs to always block. Allowlist takes precedence if IP appears in both lists. Supports IPv4 (203.0.113.0/24) and IPv6 (2001:db9::/32). Example: 203.0.113.0/24,198.51.100.1")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("")
                    .add()

                // Risk Thresholds
                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_LOW_RISK_THRESHOLD)
                    .label("Low Risk Threshold")
                    .helpText("Maximum risk score for low risk (0-100). Scores <= this value are considered low risk.")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("30")
                    .add()

                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_HIGH_RISK_THRESHOLD)
                    .label("High Risk Threshold")
                    .helpText("Minimum risk score for high risk (0-100). Scores > this value are considered high risk.")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("70")
                    .add()

                // Risk Actions
                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_LOW_RISK_ACTION)
                    .label("Low Risk Action")
                    .helpText("Action to take for low risk logins")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .options("ALLOW", "CHALLENGE", "BLOCK")
                    .defaultValue("ALLOW")
                    .add()

                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_MEDIUM_RISK_ACTION)
                    .label("Medium Risk Action")
                    .helpText("Action to take for medium risk logins (between low and high thresholds)")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .options("ALLOW", "CHALLENGE", "BLOCK")
                    .defaultValue("CHALLENGE")
                    .add()

                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_HIGH_RISK_ACTION)
                    .label("High Risk Action")
                    .helpText("Action to take for high risk logins")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .options("ALLOW", "CHALLENGE", "BLOCK")
                    .defaultValue("BLOCK")
                    .add()

                // Fail Mode
                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_FAIL_MODE)
                    .label("API Failure Mode")
                    .helpText("What to do if MaxMind API call fails: FAIL_OPEN (allow login) or FAIL_CLOSED (block login)")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .options("FAIL_OPEN", "FAIL_CLOSED")
                    .defaultValue("FAIL_OPEN")
                    .add()

                // API Timeouts
                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_CONNECT_TIMEOUT)
                    .label("Connection Timeout (ms)")
                    .helpText("Maximum time to wait for connection establishment in milliseconds. Default: 3000 (3 seconds)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("3000")
                    .add()

                .property()
                    .name(MaxMindMinFraudAuthenticator.CONFIG_READ_TIMEOUT)
                    .label("Read Timeout (ms)")
                    .helpText("Maximum time to wait for API response in milliseconds. Default: 5000 (5 seconds)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("5000")
                    .add()

                .build();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // No initialization needed
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No post-initialization needed
    }

    @Override
    public void close() {
        // Nothing to close
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
