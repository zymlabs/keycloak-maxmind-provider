package com.zymlabs.keycloak.maxmindprovider;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Collections;
import java.util.List;

/**
 * Factory for creating MaxMindPreAuthCorrelatorAuthenticator instances.
 *
 * This authenticator requires no configuration and is designed to be added
 * to authentication flows that use pre-authentication fraud detection.
 */
public class MaxMindPreAuthCorrelatorAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "maxmind-preauth-correlator";
    private static final String DISPLAY_NAME = "MaxMind Pre-Auth Correlator";
    private static final String HELP_TEXT = "Correlates pre-authentication fraud checks with user accounts. " +
            "Add this after username/password if you have MaxMind running before login. " +
            "Requires no configuration.";

    private static final MaxMindPreAuthCorrelatorAuthenticator SINGLETON = new MaxMindPreAuthCorrelatorAuthenticator();

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
        return false; // No configuration needed
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
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
        return Collections.emptyList(); // No configuration properties
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // Nothing to initialize
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Nothing to post-initialize
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
