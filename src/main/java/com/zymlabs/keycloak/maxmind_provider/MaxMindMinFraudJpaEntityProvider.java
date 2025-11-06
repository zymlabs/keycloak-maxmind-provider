package com.zymlabs.keycloak.maxmind_provider;

import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;

import java.util.Collections;
import java.util.List;

/**
 * JPA Entity Provider for MaxMind minFraud extension.
 *
 * This provider registers the MaxMindMinFraudCheckEntity with Keycloak's JPA EntityManager
 * and provides the location of the Liquibase changelog for automatic database schema creation.
 *
 * Without this provider, Keycloak will not know about the custom entity and the database
 * table will not be created automatically.
 */
public class MaxMindMinFraudJpaEntityProvider implements JpaEntityProvider {

    /**
     * Returns the list of JPA entities to register with Keycloak's persistence unit.
     *
     * @return List containing MaxMindMinFraudCheckEntity.class
     */
    @Override
    public List<Class<?>> getEntities() {
        return Collections.singletonList(MaxMindMinFraudCheckEntity.class);
    }

    /**
     * Returns the location of the Liquibase changelog file for database schema management.
     *
     * @return Path to the changelog file relative to the classpath root
     */
    @Override
    public String getChangelogLocation() {
        return "META-INF/maxmind-minfraud-changelog.xml";
    }

    /**
     * Returns the factory ID that created this provider.
     * Must match the ID returned by MaxMindMinFraudJpaEntityProviderFactory.getId()
     *
     * @return Unique factory identifier
     */
    @Override
    public String getFactoryId() {
        return "maxmind-minfraud-jpa-entity-provider";
    }

    /**
     * Cleanup method called when the provider is being destroyed.
     * No resources to clean up in this implementation.
     */
    @Override
    public void close() {
        // No resources to close
    }
}
