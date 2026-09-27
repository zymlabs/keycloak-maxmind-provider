package com.zymlabs.keycloak.maxmindprovider;

import com.zymlabs.keycloak.maxmindprovider.MaxMindMinFraudService.ApiEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MaxMind API Endpoint Parsing Tests")
class ApiEndpointTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Blank host should use the MaxMind default over HTTPS")
    void blankUsesDefault(String value) {
        assertThat(ApiEndpoint.parse(value))
                .isEqualTo(new ApiEndpoint("minfraud.maxmind.com", -1, true));
    }

    @Test
    @DisplayName("Bare host should use HTTPS on the default port")
    void bareHost() {
        assertThat(ApiEndpoint.parse("sandbox.maxmind.com"))
                .isEqualTo(new ApiEndpoint("sandbox.maxmind.com", -1, true));
    }

    @Test
    @DisplayName("Host with port should keep the port and use HTTPS")
    void hostWithPort() {
        assertThat(ApiEndpoint.parse(" minfraud.internal:8443 "))
                .isEqualTo(new ApiEndpoint("minfraud.internal", 8443, true));
    }

    @Test
    @DisplayName("http:// URL should disable HTTPS")
    void httpUrl() {
        assertThat(ApiEndpoint.parse("http://maxmind-stub:8081"))
                .isEqualTo(new ApiEndpoint("maxmind-stub", 8081, false));
    }

    @Test
    @DisplayName("https:// URL with a path should ignore the path")
    void httpsUrlWithPath() {
        assertThat(ApiEndpoint.parse("HTTPS://sandbox.maxmind.com/minfraud/v2.0"))
                .isEqualTo(new ApiEndpoint("sandbox.maxmind.com", -1, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://maxmind.com", "http://", "not a host", "https://exa mple.com"})
    @DisplayName("Invalid values should be rejected")
    void invalidValues(String value) {
        assertThatThrownBy(() -> ApiEndpoint.parse(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
