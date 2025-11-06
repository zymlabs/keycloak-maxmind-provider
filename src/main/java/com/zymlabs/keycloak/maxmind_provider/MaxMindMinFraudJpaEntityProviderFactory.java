package com.zymlabs.keycloak.maxmind_provider;

import org.keycloak.Config;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.logging.Logger;

/**
 * Factory for creating MaxMindMinFraudJpaEntityProvider instances.
 *
 * This factory is discovered by Keycloak's service loader mechanism and is responsible
 * for creating JpaEntityProvider instances that register the MaxMind minFraud entities
 * with Keycloak's JPA system.
 *
 * The factory must be registered in META-INF/services/org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory
 * for Keycloak to discover it.
 */
public class MaxMindMinFraudJpaEntityProviderFactory implements JpaEntityProviderFactory {

    private static final Logger logger = Logger.getLogger(MaxMindMinFraudJpaEntityProviderFactory.class.getName());
    private static final String PROVIDER_ID = "maxmind-minfraud-jpa-entity-provider";

    /**
     * Creates a new instance of MaxMindMinFraudJpaEntityProvider.
     *
     * @param session The Keycloak session
     * @return A new JpaEntityProvider instance
     */
    @Override
    public JpaEntityProvider create(KeycloakSession session) {
        return new MaxMindMinFraudJpaEntityProvider();
    }

    /**
     * Returns the unique identifier for this factory.
     * Must match the ID returned by MaxMindMinFraudJpaEntityProvider.getFactoryId()
     *
     * @return Unique factory identifier
     */
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    /**
     * Initialization method called when the factory is first loaded.
     *
     * @param config Configuration scope for this factory
     */
    @Override
    public void init(Config.Scope config) {
        logger.info("Initializing MaxMindMinFraudJpaEntityProviderFactory");
    }

    /**
     * Post-initialization method called after all factories have been initialized.
     * This is where entity registration and Liquibase changelog execution happens.
     *
     * @param factory The Keycloak session factory
     */
    @Override
    public void postInit(KeycloakSessionFactory factory) {
        logger.info("MaxMindMinFraudJpaEntityProviderFactory post-initialization complete - entities registered");
    }

    /**
     * Cleanup method called when the factory is being destroyed.
     */
    @Override
    public void close() {
        logger.info("Closing MaxMindMinFraudJpaEntityProviderFactory");
    }
}
