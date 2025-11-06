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
 * Factory for MaxMindMfaEnforcerAuthenticator.
 *
 * This factory registers the MFA enforcer authenticator with Keycloak and defines
 * its configuration properties visible in the admin console.
 */
public class MaxMindMfaEnforcerAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "maxmind-mfa-enforcer";

    private static final MaxMindMfaEnforcerAuthenticator SINGLETON = new MaxMindMfaEnforcerAuthenticator();

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public String getDisplayType() {
        return "MaxMind MFA Enforcer";
    }

    @Override
    public String getReferenceCategory() {
        return "maxmind";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Enforces MFA requirement when MaxMind fraud detection triggers CHALLENGE action. " +
               "Blocks users WITHOUT configured MFA credentials to prevent OTP setup during suspicious logins. " +
               "Users WITH MFA configured are allowed to proceed to MFA verification. " +
               "Place this authenticator AFTER MaxMind minFraud and BEFORE conditional MFA flows.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                    .name(MaxMindMfaEnforcerAuthenticator.CONFIG_MFA_CREDENTIAL_TYPES)
                    .label("MFA Credential Types")
                    .helpText("Comma-separated list of credential types to check for MFA enforcement. " +
                              "Supported types: otp (TOTP/HOTP), webauthn (passkeys/security keys), " +
                              "sms-otp (if SMS OTP extension installed). " +
                              "User must have at least ONE of these configured to pass the check.")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("otp,webauthn")
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
