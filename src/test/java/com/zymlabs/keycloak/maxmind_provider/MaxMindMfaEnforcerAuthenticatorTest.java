package com.zymlabs.keycloak.maxmind_provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

/**
 * Tests for MaxMindMfaEnforcerAuthenticator.
 *
 * These tests verify that MFA enforcement works correctly when MaxMind triggers
 * CHALLENGE action, blocking users without MFA and allowing users with MFA.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MaxMind MFA Enforcer Authenticator Tests")
class MaxMindMfaEnforcerAuthenticatorTest {

    @Mock
    private AuthenticationFlowContext context;

    @Mock
    private EventBuilder eventBuilder;

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private AuthenticatorConfigModel authenticatorConfig;

    @Mock
    private AuthenticationSessionModel authenticationSession;

    @Mock
    private UserCredentialManager credentialManager;

    private MaxMindMfaEnforcerAuthenticator authenticator;
    private Map<String, String> config;

    @BeforeEach
    void setup() {
        authenticator = new MaxMindMfaEnforcerAuthenticator();

        // Setup base configuration
        config = new HashMap<>();
        config.put("mfaCredentialTypes", "otp,webauthn");

        // Setup mock behavior
        when(context.getEvent()).thenReturn(eventBuilder);
        when(eventBuilder.detail(anyString(), anyString())).thenReturn(eventBuilder);
        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(realm);
        when(context.getUser()).thenReturn(user);
        when(context.getAuthenticatorConfig()).thenReturn(authenticatorConfig);
        when(authenticatorConfig.getConfig()).thenReturn(config);
        when(context.getAuthenticationSession()).thenReturn(authenticationSession);
        when(user.getId()).thenReturn("user-123");
        when(user.getUsername()).thenReturn("testuser");
        when(user.credentialManager()).thenReturn(credentialManager);

        // Mock form provider for error handling
        lenient().when(context.form()).thenReturn(mock(org.keycloak.forms.login.LoginFormsProvider.class, RETURNS_DEEP_STUBS));
    }

    @Test
    @DisplayName("No MaxMind challenge note - should allow authentication")
    void testNoChallenge_ShouldAllow() {
        // Given: No challenge note set
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn(null);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should call success without checking MFA
        verify(context).success();
        verify(credentialManager, never()).isConfiguredFor(anyString());
        verify(context, never()).failure(any(), any());
    }

    @Test
    @DisplayName("Challenge note is false - should allow authentication")
    void testChallengeFalse_ShouldAllow() {
        // Given: Challenge note is "false"
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("false");

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should call success without checking MFA
        verify(context).success();
        verify(credentialManager, never()).isConfiguredFor(anyString());
        verify(context, never()).failure(any(), any());
    }

    @Test
    @DisplayName("Challenge + user has OTP - should allow authentication")
    void testChallengeWithOtp_ShouldAllow() {
        // Given: Challenge note set and user has OTP
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(authenticationSession.getAuthNote("maxmind_risk_score")).thenReturn("45.50");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(true);
        when(credentialManager.isConfiguredFor("webauthn")).thenReturn(false);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should call success and log allowed event
        verify(context).success();
        verify(context, never()).failure(any(), any());
        verify(eventBuilder).detail("maxmind_mfa_enforcer_decision", "ALLOWED");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_type", "otp");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_risk_score", "45.50");
    }

    @Test
    @DisplayName("Challenge + user has WebAuthn - should allow authentication")
    void testChallengeWithWebAuthn_ShouldAllow() {
        // Given: Challenge note set and user has WebAuthn
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(authenticationSession.getAuthNote("maxmind_risk_score")).thenReturn("50.00");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(false);
        when(credentialManager.isConfiguredFor("webauthn")).thenReturn(true);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should call success and log allowed event
        verify(context).success();
        verify(context, never()).failure(any(), any());
        verify(eventBuilder).detail("maxmind_mfa_enforcer_decision", "ALLOWED");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_type", "webauthn");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_risk_score", "50.00");
    }

    @Test
    @DisplayName("Challenge + user has both OTP and WebAuthn - should allow with first found")
    void testChallengeWithMultipleMfa_ShouldAllow() {
        // Given: Challenge note set and user has both OTP and WebAuthn
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(authenticationSession.getAuthNote("maxmind_risk_score")).thenReturn("55.75");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(true);
        when(credentialManager.isConfiguredFor("webauthn")).thenReturn(true);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should call success (check stops at first match - OTP)
        verify(context).success();
        verify(context, never()).failure(any(), any());
        verify(eventBuilder).detail("maxmind_mfa_enforcer_decision", "ALLOWED");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_type", "otp");
    }

    @Test
    @DisplayName("Challenge + user has NO MFA - should block authentication")
    void testChallengeWithoutMfa_ShouldBlock() {
        // Given: Challenge note set and user has NO MFA
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(authenticationSession.getAuthNote("maxmind_risk_score")).thenReturn("65.00");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(false);
        when(credentialManager.isConfiguredFor("webauthn")).thenReturn(false);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should call failure and log blocked event
        verify(context, never()).success();
        verify(context).failure(eq(AuthenticationFlowError.INVALID_CREDENTIALS), any(Response.class));
        verify(eventBuilder).detail("maxmind_mfa_enforcer_decision", "BLOCKED");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_reason", "No MFA configured");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_risk_score", "65.00");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_checked_types", "otp,webauthn");
    }

    @Test
    @DisplayName("Challenge without risk score - should still check MFA")
    void testChallengeWithoutRiskScore_ShouldStillCheck() {
        // Given: Challenge note set but no risk score, user has OTP
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(authenticationSession.getAuthNote("maxmind_risk_score")).thenReturn(null);
        when(credentialManager.isConfiguredFor("otp")).thenReturn(true);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should call success and log "unknown" risk score
        verify(context).success();
        verify(eventBuilder).detail("maxmind_mfa_enforcer_decision", "ALLOWED");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_risk_score", "unknown");
    }

    @Test
    @DisplayName("Custom MFA credential types - should check configured types")
    void testCustomMfaTypes_ShouldCheckConfigured() {
        // Given: Custom MFA types configured (sms-otp)
        config.put("mfaCredentialTypes", "sms-otp,email-otp");
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(authenticationSession.getAuthNote("maxmind_risk_score")).thenReturn("40.00");
        when(credentialManager.isConfiguredFor("sms-otp")).thenReturn(true);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should call success and check custom types
        verify(context).success();
        verify(credentialManager).isConfiguredFor("sms-otp");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_decision", "ALLOWED");
        verify(eventBuilder).detail("maxmind_mfa_enforcer_type", "sms-otp");
    }

    @Test
    @DisplayName("Custom MFA types not configured - should use defaults")
    void testMissingMfaTypesConfig_ShouldUseDefaults() {
        // Given: No mfaCredentialTypes config (should default to "otp,webauthn")
        config.remove("mfaCredentialTypes");
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(true);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should check default types
        verify(credentialManager).isConfiguredFor("otp");
        verify(context).success();
    }

    @Test
    @DisplayName("Empty MFA types config - should use defaults")
    void testEmptyMfaTypesConfig_ShouldUseDefaults() {
        // Given: Empty mfaCredentialTypes config
        config.put("mfaCredentialTypes", "");
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(true);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should check default types
        verify(credentialManager).isConfiguredFor("otp");
        verify(context).success();
    }

    @Test
    @DisplayName("No authenticator config - should use defaults")
    void testNoAuthenticatorConfig_ShouldUseDefaults() {
        // Given: No authenticator config
        when(context.getAuthenticatorConfig()).thenReturn(null);
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(true);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should check default types
        verify(credentialManager).isConfiguredFor("otp");
        verify(context).success();
    }

    @Test
    @DisplayName("MFA types with whitespace - should trim types")
    void testMfaTypesWithWhitespace_ShouldTrim() {
        // Given: MFA types with extra whitespace
        config.put("mfaCredentialTypes", " otp , webauthn , sms-otp ");
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(false);
        when(credentialManager.isConfiguredFor("webauthn")).thenReturn(false);
        when(credentialManager.isConfiguredFor("sms-otp")).thenReturn(true);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should trim and check types correctly
        verify(credentialManager).isConfiguredFor("otp");
        verify(credentialManager).isConfiguredFor("webauthn");
        verify(credentialManager).isConfiguredFor("sms-otp");
        verify(context).success();
    }

    @Test
    @DisplayName("Action method - should call authenticate")
    void testAction_ShouldCallAuthenticate() {
        // Given: No challenge note
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn(null);

        // When: Action is called
        authenticator.action(context);

        // Then: Should behave like authenticate
        verify(context).success();
    }

    @Test
    @DisplayName("requiresUser - should return true")
    void testRequiresUser() {
        assertThat(authenticator.requiresUser()).isTrue();
    }

    @Test
    @DisplayName("configuredFor - should return true")
    void testConfiguredFor() {
        boolean result = authenticator.configuredFor(session, realm, user);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("setRequiredActions - should not throw")
    void testSetRequiredActions() {
        assertThatCode(() -> authenticator.setRequiredActions(session, realm, user))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("close - should not throw")
    void testClose() {
        assertThatCode(() -> authenticator.close())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Helper method - hasAnyMfaConfigured with OTP")
    void testHelperMethod_HasOtp() {
        // Given: User has OTP
        when(credentialManager.isConfiguredFor("otp")).thenReturn(true);

        // When: Check helper method
        boolean result = MaxMindMfaEnforcerAuthenticator.hasAnyMfaConfigured(user, "otp,webauthn");

        // Then: Should return true
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Helper method - hasAnyMfaConfigured without any MFA")
    void testHelperMethod_NoMfa() {
        // Given: User has no MFA
        when(credentialManager.isConfiguredFor("otp")).thenReturn(false);
        when(credentialManager.isConfiguredFor("webauthn")).thenReturn(false);

        // When: Check helper method
        boolean result = MaxMindMfaEnforcerAuthenticator.hasAnyMfaConfigured(user, "otp,webauthn");

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Event logging includes auth_method")
    void testEventLogging_IncludesAuthMethod() {
        // Given: Challenge with user having OTP
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(true);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should log auth_method
        verify(eventBuilder).detail("auth_method", "maxmind_mfa_enforcer");
    }

    @Test
    @DisplayName("Blocked user gets error page with risk score")
    void testBlockedUser_GetsErrorPageWithRiskScore() {
        // Given: Challenge with no MFA and risk score
        when(authenticationSession.getAuthNote("maxmind_challenge")).thenReturn("true");
        when(authenticationSession.getAuthNote("maxmind_risk_score")).thenReturn("75.25");
        when(credentialManager.isConfiguredFor("otp")).thenReturn(false);
        when(credentialManager.isConfiguredFor("webauthn")).thenReturn(false);

        // Mock form chain
        org.keycloak.forms.login.LoginFormsProvider mockForm =
            mock(org.keycloak.forms.login.LoginFormsProvider.class, RETURNS_DEEP_STUBS);
        when(context.form()).thenReturn(mockForm);
        when(mockForm.setAttribute(anyString(), anyString())).thenReturn(mockForm);
        when(mockForm.setError(anyString())).thenReturn(mockForm);

        // When: Authenticate is called
        authenticator.authenticate(context);

        // Then: Should set risk score attribute and error
        verify(mockForm).setAttribute("riskScore", "75.25");
        verify(mockForm).setError("mfaRequiredForSuspiciousActivity");
        verify(mockForm).createErrorPage(Response.Status.FORBIDDEN);
    }
}
