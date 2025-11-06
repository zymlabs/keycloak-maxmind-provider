package com.zymlabs.keycloak.maxmind_provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.Config;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MaxMindMinFraudJpaEntityProviderFactory.
 *
 * Verifies that the factory correctly creates JPA entity providers and manages
 * their lifecycle according to Keycloak's provider factory contract.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MaxMind JPA Entity Provider Factory Tests")
class MaxMindMinFraudJpaEntityProviderFactoryTest {

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakSessionFactory sessionFactory;

    @Mock
    private Config.Scope config;

    private MaxMindMinFraudJpaEntityProviderFactory factory;

    @BeforeEach
    void setup() {
        factory = new MaxMindMinFraudJpaEntityProviderFactory();
    }

    @Test
    @DisplayName("create() should return new MaxMindMinFraudJpaEntityProvider instance")
    void testCreate_ReturnsProviderInstance() {
        // When
        JpaEntityProvider provider = factory.create(session);

        // Then
        assertThat(provider)
                .as("Provider should not be null")
                .isNotNull();

        assertThat(provider)
                .as("Provider should be instance of MaxMindMinFraudJpaEntityProvider")
                .isInstanceOf(MaxMindMinFraudJpaEntityProvider.class);
    }

    @Test
    @DisplayName("create() should return new instance on each call")
    void testCreate_ReturnsNewInstance() {
        // When
        JpaEntityProvider provider1 = factory.create(session);
        JpaEntityProvider provider2 = factory.create(session);

        // Then
        assertThat(provider1)
                .as("Each call should return a new instance")
                .isNotSameAs(provider2);
    }

    @Test
    @DisplayName("create() should not throw exception with null session")
    void testCreate_HandlesNullSession() {
        // When/Then - should not throw exception
        assertThatNoException()
                .as("create() should handle null session gracefully")
                .isThrownBy(() -> factory.create(null));
    }

    @Test
    @DisplayName("getId() should return correct factory ID")
    void testGetId_ReturnsCorrectId() {
        // When
        String id = factory.getId();

        // Then
        assertThat(id)
                .as("Factory ID should not be null")
                .isNotNull();

        assertThat(id)
                .as("Factory ID should match expected value")
                .isEqualTo("maxmind-minfraud-jpa-entity-provider");
    }

    @Test
    @DisplayName("getId() should match provider's getFactoryId()")
    void testGetId_MatchesProviderFactoryId() {
        // Given
        JpaEntityProvider provider = factory.create(session);

        // When
        String factoryId = factory.getId();
        String providerFactoryId = provider.getFactoryId();

        // Then
        assertThat(factoryId)
                .as("Factory ID should match provider's factory ID")
                .isEqualTo(providerFactoryId);
    }

    @Test
    @DisplayName("Multiple calls to getId() should return same value")
    void testGetId_ConsistentResults() {
        // When
        String id1 = factory.getId();
        String id2 = factory.getId();

        // Then
        assertThat(id1)
                .as("Multiple calls should return same value")
                .isEqualTo(id2);
    }

    @Test
    @DisplayName("init() should execute without errors")
    void testInit_ExecutesSuccessfully() {
        // When/Then - should not throw any exception
        assertThatNoException()
                .as("init() should execute without throwing exceptions")
                .isThrownBy(() -> factory.init(config));
    }

    @Test
    @DisplayName("init() should handle null config")
    void testInit_HandlesNullConfig() {
        // When/Then - should not throw any exception
        assertThatNoException()
                .as("init() should handle null config gracefully")
                .isThrownBy(() -> factory.init(null));
    }

    @Test
    @DisplayName("postInit() should execute without errors")
    void testPostInit_ExecutesSuccessfully() {
        // When/Then - should not throw any exception
        assertThatNoException()
                .as("postInit() should execute without throwing exceptions")
                .isThrownBy(() -> factory.postInit(sessionFactory));
    }

    @Test
    @DisplayName("postInit() should handle null session factory")
    void testPostInit_HandlesNullSessionFactory() {
        // When/Then - should not throw any exception
        assertThatNoException()
                .as("postInit() should handle null session factory gracefully")
                .isThrownBy(() -> factory.postInit(null));
    }

    @Test
    @DisplayName("close() should execute without errors")
    void testClose_ExecutesSuccessfully() {
        // When/Then - should not throw any exception
        assertThatNoException()
                .as("close() should execute without throwing exceptions")
                .isThrownBy(() -> factory.close());
    }

    @Test
    @DisplayName("Factory should support full lifecycle")
    void testFullLifecycle() {
        // When/Then - Full lifecycle should execute without errors
        assertThatNoException()
                .as("Full lifecycle should execute successfully")
                .isThrownBy(() -> {
                    factory.init(config);
                    factory.postInit(sessionFactory);
                    JpaEntityProvider provider = factory.create(session);
                    assertThat(provider).isNotNull();
                    provider.close();
                    factory.close();
                });
    }

    @Test
    @DisplayName("Factory should implement JpaEntityProviderFactory interface")
    void testImplementsInterface() {
        // Then
        assertThat(factory)
                .as("Factory should implement JpaEntityProviderFactory")
                .isInstanceOf(org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory.class);
    }

    @Test
    @DisplayName("Factory ID should not be empty")
    void testGetId_NotEmpty() {
        // When
        String id = factory.getId();

        // Then
        assertThat(id)
                .as("Factory ID should not be empty")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Created provider should have valid factory ID")
    void testCreatedProvider_HasValidFactoryId() {
        // When
        JpaEntityProvider provider = factory.create(session);

        // Then
        assertThat(provider.getFactoryId())
                .as("Created provider should have non-null factory ID")
                .isNotNull()
                .as("Created provider's factory ID should not be empty")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Created provider should have valid entities")
    void testCreatedProvider_HasValidEntities() {
        // When
        JpaEntityProvider provider = factory.create(session);

        // Then
        assertThat(provider.getEntities())
                .as("Created provider should have non-null entities list")
                .isNotNull()
                .as("Created provider should have at least one entity")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Created provider should have valid changelog location")
    void testCreatedProvider_HasValidChangelogLocation() {
        // When
        JpaEntityProvider provider = factory.create(session);

        // Then
        assertThat(provider.getChangelogLocation())
                .as("Created provider should have non-null changelog location")
                .isNotNull()
                .as("Created provider's changelog location should not be empty")
                .isNotEmpty();
    }
}
