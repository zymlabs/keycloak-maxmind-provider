package com.zymlabs.keycloak.maxmind_provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for MaxMindMinFraudJpaEntityProvider.
 *
 * Verifies that the JPA entity provider correctly registers the MaxMindMinFraudCheckEntity
 * with Keycloak's persistence system and provides the correct Liquibase changelog location.
 */
@DisplayName("MaxMind JPA Entity Provider Tests")
class MaxMindMinFraudJpaEntityProviderTest {

    private MaxMindMinFraudJpaEntityProvider provider;

    @BeforeEach
    void setup() {
        provider = new MaxMindMinFraudJpaEntityProvider();
    }

    @Test
    @DisplayName("getEntities() should return list containing MaxMindMinFraudCheckEntity")
    void testGetEntities_ReturnsCorrectEntity() {
        // When
        List<Class<?>> entities = provider.getEntities();

        // Then
        assertThat(entities)
                .as("Entity list should not be null")
                .isNotNull();

        assertThat(entities)
                .as("Entity list should contain exactly one entity")
                .hasSize(1);

        assertThat(entities)
                .as("Entity list should contain MaxMindMinFraudCheckEntity.class")
                .containsExactly(MaxMindMinFraudCheckEntity.class);
    }

    @Test
    @DisplayName("getEntities() should return immutable list")
    void testGetEntities_ReturnsImmutableList() {
        // When
        List<Class<?>> entities = provider.getEntities();

        // Then
        assertThatThrownBy(() -> entities.add(Object.class))
                .as("List should be immutable")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getChangelogLocation() should return correct changelog path")
    void testGetChangelogLocation_ReturnsCorrectPath() {
        // When
        String changelogLocation = provider.getChangelogLocation();

        // Then
        assertThat(changelogLocation)
                .as("Changelog location should not be null")
                .isNotNull();

        assertThat(changelogLocation)
                .as("Changelog location should be in META-INF directory")
                .isEqualTo("META-INF/maxmind-minfraud-changelog.xml");
    }

    @Test
    @DisplayName("Changelog file should exist at specified location")
    void testChangelogFile_Exists() {
        // Given
        String changelogLocation = provider.getChangelogLocation();

        // When
        InputStream changelogStream = getClass().getClassLoader()
                .getResourceAsStream(changelogLocation);

        // Then
        assertThat(changelogStream)
                .as("Changelog file should exist at location: " + changelogLocation)
                .isNotNull();
    }

    @Test
    @DisplayName("getFactoryId() should return correct factory ID")
    void testGetFactoryId_ReturnsCorrectId() {
        // When
        String factoryId = provider.getFactoryId();

        // Then
        assertThat(factoryId)
                .as("Factory ID should not be null")
                .isNotNull();

        assertThat(factoryId)
                .as("Factory ID should match expected value")
                .isEqualTo("maxmind-minfraud-jpa-entity-provider");
    }

    @Test
    @DisplayName("getFactoryId() should match factory's getId()")
    void testGetFactoryId_MatchesFactoryId() {
        // Given
        MaxMindMinFraudJpaEntityProviderFactory factory = new MaxMindMinFraudJpaEntityProviderFactory();

        // When
        String providerFactoryId = provider.getFactoryId();
        String factoryId = factory.getId();

        // Then
        assertThat(providerFactoryId)
                .as("Provider's factory ID should match factory's ID")
                .isEqualTo(factoryId);
    }

    @Test
    @DisplayName("close() should execute without errors")
    void testClose_ExecutesSuccessfully() {
        // When/Then - should not throw any exception
        assertThatNoException()
                .as("close() should execute without throwing exceptions")
                .isThrownBy(() -> provider.close());
    }

    @Test
    @DisplayName("Multiple calls to getEntities() should return equivalent lists")
    void testGetEntities_ConsistentResults() {
        // When
        List<Class<?>> entities1 = provider.getEntities();
        List<Class<?>> entities2 = provider.getEntities();

        // Then
        assertThat(entities1)
                .as("Multiple calls should return equivalent results")
                .isEqualTo(entities2);
    }

    @Test
    @DisplayName("Multiple calls to getChangelogLocation() should return same value")
    void testGetChangelogLocation_ConsistentResults() {
        // When
        String location1 = provider.getChangelogLocation();
        String location2 = provider.getChangelogLocation();

        // Then
        assertThat(location1)
                .as("Multiple calls should return same value")
                .isEqualTo(location2);
    }

    @Test
    @DisplayName("Multiple calls to getFactoryId() should return same value")
    void testGetFactoryId_ConsistentResults() {
        // When
        String id1 = provider.getFactoryId();
        String id2 = provider.getFactoryId();

        // Then
        assertThat(id1)
                .as("Multiple calls should return same value")
                .isEqualTo(id2);
    }

    @Test
    @DisplayName("Provider should implement JpaEntityProvider interface")
    void testImplementsInterface() {
        // Then
        assertThat(provider)
                .as("Provider should implement JpaEntityProvider")
                .isInstanceOf(org.keycloak.connections.jpa.entityprovider.JpaEntityProvider.class);
    }
}
