package com.zymlabs.keycloak.maxmindprovider;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.Date;
import java.util.List;

/**
 * Keycloak Authenticator that correlates pre-authentication fraud checks with user accounts.
 *
 * This authenticator must be placed AFTER username/password authentication in the flow.
 * It finds any pre-auth fraud checks for the current session and updates them with the
 * authenticated user's information for complete audit trail.
 *
 * This authenticator always succeeds and never blocks authentication.
 */
public class MaxMindPreAuthCorrelatorAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(MaxMindPreAuthCorrelatorAuthenticator.class);

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null) {
            // This should never happen since requiresUser() = true
            logger.warn("Pre-Auth Correlator called without user - skipping correlation");
            context.success();
            return;
        }

        String sessionId = context.getAuthenticationSession().getParentSession().getId();
        String userId = user.getId();
        String username = user.getUsername();
        String email = user.getEmail();

        logger.debugf("Attempting to correlate pre-auth fraud checks for session %s with user %s",
                     sessionId, username);

        try {
            // Use direct EntityManager access
            EntityManager em = context.getSession()
                    .getProvider(org.keycloak.connections.jpa.JpaConnectionProvider.class)
                    .getEntityManager();

            // Find pre-auth checks for this session
            TypedQuery<MaxMindMinFraudCheckEntity> query = em.createNamedQuery(
                    "findBySessionIdPreAuth", MaxMindMinFraudCheckEntity.class);
            query.setParameter("sessionId", sessionId);

            List<MaxMindMinFraudCheckEntity> preAuthChecks = query.getResultList();

            if (preAuthChecks.isEmpty()) {
                logger.debugf("No pre-auth fraud checks found for session %s", sessionId);
            } else {
                // Update each pre-auth check with user information
                for (MaxMindMinFraudCheckEntity check : preAuthChecks) {
                    check.setUserId(userId);
                    check.setUsername(username);
                    check.setEmail(email);
                    check.setIsPreAuth(false);  // Mark as correlated
                    check.setCorrelatedAt(new Date());

                    em.merge(check);

                    logger.infof("Correlated pre-auth fraud check (id=%d, risk_score=%.2f, decision=%s) with user %s",
                               check.getId(), check.getRiskScore(), check.getDecision(), username);
                }

                logger.infof("Successfully correlated %d pre-auth fraud check(s) for user %s",
                           preAuthChecks.size(), username);
            }
        } catch (Exception e) {
            // Log error but don't fail authentication
            logger.errorf(e, "Error correlating pre-auth fraud checks for session %s - continuing authentication",
                        sessionId);
        }

        // Always succeed - correlation is for audit purposes only
        context.success();
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // Not used - this authenticator doesn't present forms
    }

    @Override
    public boolean requiresUser() {
        return true; // Must run after user is identified
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
}
