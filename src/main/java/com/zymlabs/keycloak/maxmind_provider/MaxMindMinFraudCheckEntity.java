package com.zymlabs.keycloak.maxmind_provider;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * JPA Entity for storing MaxMind minFraud check results.
 *
 * This entity stores fraud detection results for analysis and auditing purposes.
 * Each record represents a single fraud check performed during authentication.
 */
@Entity
@Table(name = "maxmind_minfraud_check", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_realm_id", columnList = "realm_id"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_user_timestamp", columnList = "user_id,timestamp"),
    @Index(name = "idx_event_id", columnList = "event_id")
})
@NamedQueries({
    @NamedQuery(name = "findByUserId",
                query = "SELECT e FROM MaxMindMinFraudCheckEntity e WHERE e.userId = :userId ORDER BY e.timestamp DESC"),
    @NamedQuery(name = "findByRealmId",
                query = "SELECT e FROM MaxMindMinFraudCheckEntity e WHERE e.realmId = :realmId ORDER BY e.timestamp DESC"),
    @NamedQuery(name = "findByUserIdAndDateRange",
                query = "SELECT e FROM MaxMindMinFraudCheckEntity e WHERE e.userId = :userId AND e.timestamp BETWEEN :startDate AND :endDate ORDER BY e.timestamp DESC"),
    @NamedQuery(name = "findHighRiskByRealm",
                query = "SELECT e FROM MaxMindMinFraudCheckEntity e WHERE e.realmId = :realmId AND e.riskScore >= :minRiskScore ORDER BY e.timestamp DESC")
})
public class MaxMindMinFraudCheckEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "realm_id", nullable = false, length = 36)
    private String realmId;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "timestamp", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Column(name = "decision", nullable = false, length = 20)
    private String decision; // ALLOWED, CHALLENGED, BLOCKED

    @Column(name = "device_session_id", length = 255)
    private String deviceSessionId;

    @Column(name = "request_id", length = 36)
    private String requestId; // MaxMind request ID for tracking

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse; // Full JSON response from MaxMind

    @Column(name = "service_level", length = 20)
    private String serviceLevel; // SCORE, INSIGHTS, FACTORS

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage; // Store error if API call failed

    @Column(name = "event_id", length = 36)
    private String eventId; // Keycloak event ID for correlation

    // Constructors
    public MaxMindMinFraudCheckEntity() {
        this.timestamp = new Date();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRealmId() {
        return realmId;
    }

    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Double riskScore) {
        this.riskScore = riskScore;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getDeviceSessionId() {
        return deviceSessionId;
    }

    public void setDeviceSessionId(String deviceSessionId) {
        this.deviceSessionId = deviceSessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    @Override
    public String toString() {
        return "MaxMindMinFraudCheckEntity{" +
                "id=" + id +
                ", userId='" + userId + '\'' +
                ", realmId='" + realmId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", timestamp=" + timestamp +
                ", riskScore=" + riskScore +
                ", decision='" + decision + '\'' +
                '}';
    }
}
