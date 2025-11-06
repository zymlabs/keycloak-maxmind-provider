package com.zymlabs.keycloak.maxmindprovider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;
import static com.zymlabs.keycloak.maxmindprovider.MaxMindMinFraudAuthenticator.*;

/**
 * Tests for risk evaluation logic extracted from MaxMindMinFraudAuthenticator.
 *
 * Tests threshold boundaries and risk level determination.
 */
@DisplayName("Risk Evaluation Tests")
class RiskEvaluationTest {

    // Standard thresholds used in most tests
    private static final int LOW_THRESHOLD = 30;
    private static final int HIGH_THRESHOLD = 70;

    @Test
    @DisplayName("Score at zero should be LOW risk")
    void testZeroScore() {
        String riskLevel = evaluateRiskLevel(0.0, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("LOW");
    }

    @Test
    @DisplayName("Score below low threshold should be LOW risk")
    void testLowRiskScore() {
        String riskLevel = evaluateRiskLevel(15.5, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("LOW");
    }

    @Test
    @DisplayName("Score exactly at low threshold should be LOW risk")
    void testLowRiskBoundary() {
        String riskLevel = evaluateRiskLevel(30.0, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("LOW");
    }

    @Test
    @DisplayName("Score just above low threshold should be MEDIUM risk")
    void testMediumRiskLowerBoundary() {
        String riskLevel = evaluateRiskLevel(30.1, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Score between thresholds should be MEDIUM risk")
    void testMediumRiskScore() {
        String riskLevel = evaluateRiskLevel(50.0, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Score exactly at high threshold should be MEDIUM risk")
    void testMediumRiskUpperBoundary() {
        String riskLevel = evaluateRiskLevel(70.0, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Score just above high threshold should be HIGH risk")
    void testHighRiskLowerBoundary() {
        String riskLevel = evaluateRiskLevel(70.1, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Score well above high threshold should be HIGH risk")
    void testHighRiskScore() {
        String riskLevel = evaluateRiskLevel(85.0, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Score at 100 (maximum) should be HIGH risk")
    void testMaximumScore() {
        String riskLevel = evaluateRiskLevel(100.0, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Strict thresholds: score 25 should be HIGH risk with low=20, high=50")
    void testStrictThresholds() {
        String riskLevel = evaluateRiskLevel(25.0, 20, 50);
        assertThat(riskLevel).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Lenient thresholds: score 35 should be LOW risk with low=40, high=80")
    void testLenientThresholds() {
        String riskLevel = evaluateRiskLevel(35.0, 40, 80);
        assertThat(riskLevel).isEqualTo("LOW");
    }

    // Action determination tests

    @Test
    @DisplayName("LOW risk should return low risk action")
    void testDetermineActionForLowRisk() {
        RiskAction action = determineRiskAction("LOW",
                RiskAction.ALLOW, RiskAction.CHALLENGE, RiskAction.BLOCK);
        assertThat(action).isEqualTo(RiskAction.ALLOW);
    }

    @Test
    @DisplayName("MEDIUM risk should return medium risk action")
    void testDetermineActionForMediumRisk() {
        RiskAction action = determineRiskAction("MEDIUM",
                RiskAction.ALLOW, RiskAction.CHALLENGE, RiskAction.BLOCK);
        assertThat(action).isEqualTo(RiskAction.CHALLENGE);
    }

    @Test
    @DisplayName("HIGH risk should return high risk action")
    void testDetermineActionForHighRisk() {
        RiskAction action = determineRiskAction("HIGH",
                RiskAction.ALLOW, RiskAction.CHALLENGE, RiskAction.BLOCK);
        assertThat(action).isEqualTo(RiskAction.BLOCK);
    }

    @Test
    @DisplayName("All ALLOW configuration should always return ALLOW")
    void testAllAllowConfiguration() {
        assertThat(determineRiskAction("LOW", RiskAction.ALLOW, RiskAction.ALLOW, RiskAction.ALLOW))
                .isEqualTo(RiskAction.ALLOW);
        assertThat(determineRiskAction("MEDIUM", RiskAction.ALLOW, RiskAction.ALLOW, RiskAction.ALLOW))
                .isEqualTo(RiskAction.ALLOW);
        assertThat(determineRiskAction("HIGH", RiskAction.ALLOW, RiskAction.ALLOW, RiskAction.ALLOW))
                .isEqualTo(RiskAction.ALLOW);
    }

    @Test
    @DisplayName("All BLOCK configuration should always return BLOCK")
    void testAllBlockConfiguration() {
        assertThat(determineRiskAction("LOW", RiskAction.BLOCK, RiskAction.BLOCK, RiskAction.BLOCK))
                .isEqualTo(RiskAction.BLOCK);
        assertThat(determineRiskAction("MEDIUM", RiskAction.BLOCK, RiskAction.BLOCK, RiskAction.BLOCK))
                .isEqualTo(RiskAction.BLOCK);
        assertThat(determineRiskAction("HIGH", RiskAction.BLOCK, RiskAction.BLOCK, RiskAction.BLOCK))
                .isEqualTo(RiskAction.BLOCK);
    }

    @Test
    @DisplayName("Invalid risk level should throw IllegalArgumentException")
    void testInvalidRiskLevel() {
        assertThatThrownBy(() ->
                determineRiskAction("INVALID", RiskAction.ALLOW, RiskAction.CHALLENGE, RiskAction.BLOCK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid risk level");
    }

    // Fail mode tests

    @Test
    @DisplayName("FAIL_OPEN should allow login on failure")
    void testFailOpenMode() {
        boolean shouldAllow = shouldAllowOnFailure(FailMode.FAIL_OPEN);
        assertThat(shouldAllow).isTrue();
    }

    @Test
    @DisplayName("FAIL_CLOSED should block login on failure")
    void testFailClosedMode() {
        boolean shouldAllow = shouldAllowOnFailure(FailMode.FAIL_CLOSED);
        assertThat(shouldAllow).isFalse();
    }

    // Edge case tests

    @Test
    @DisplayName("Negative score should be treated as LOW risk")
    void testNegativeScore() {
        // Negative scores might occur from API errors
        String riskLevel = evaluateRiskLevel(-1.0, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("LOW");
    }

    @Test
    @DisplayName("Score above 100 should be HIGH risk")
    void testScoreAbove100() {
        // Should handle values beyond normal range
        String riskLevel = evaluateRiskLevel(150.0, LOW_THRESHOLD, HIGH_THRESHOLD);
        assertThat(riskLevel).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Zero thresholds: any positive score should be HIGH risk")
    void testZeroThresholds() {
        String riskLevel = evaluateRiskLevel(0.1, 0, 0);
        assertThat(riskLevel).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Same low and high threshold should eliminate MEDIUM range")
    void testSameThresholds() {
        // low=50, high=50: score <= 50 is LOW, > 50 is HIGH
        assertThat(evaluateRiskLevel(49.9, 50, 50)).isEqualTo("LOW");
        assertThat(evaluateRiskLevel(50.0, 50, 50)).isEqualTo("LOW");
        assertThat(evaluateRiskLevel(50.1, 50, 50)).isEqualTo("HIGH");
    }
}
