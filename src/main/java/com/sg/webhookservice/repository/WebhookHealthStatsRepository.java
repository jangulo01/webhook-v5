package com.sg.webhookservice.repository;

import com.sg.webhookservice.model.WebhookHealthStats;
import com.sg.webhookservice.model.WebhookHealthStats.HealthStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository to access and manage WebhookHealthStats entities.
 *
 * Provides methods for querying, analyzing, and updating health statistics
 * for webhook configurations.
 */
@Repository
public interface WebhookHealthStatsRepository extends JpaRepository<WebhookHealthStats, UUID> {

    /**
     * Find health statistics by webhook configuration ID
     *
     * @param webhookConfigId ID of the webhook configuration
     * @return Optional health statistics
     */
    Optional<WebhookHealthStats> findByWebhookConfigId(UUID webhookConfigId);

    /**
     * Find health statistics by webhook name
     *
     * @param webhookName Name of the webhook
     * @return Optional health statistics
     */
    Optional<WebhookHealthStats> findByWebhookName(String webhookName);

    /**
     * Find health statistics for all active webhooks
     *
     * @return List of health statistics
     */
    @Query("SELECT hs FROM WebhookHealthStats hs JOIN hs.webhookConfig wc WHERE wc.active = true")
    List<WebhookHealthStats> findAllForActiveWebhooks();

    /**
     * Find health statistics by health status
     *
     * @param status Health status to filter by
     * @param pageable Pagination and sorting configuration
     * @return Page of health statistics with specified status
     */
    Page<WebhookHealthStats> findByHealthStatus(HealthStatus status, Pageable pageable);

    /**
     * Find webhooks with health issues (DEGRADED or UNHEALTHY)
     *
     * @param pageable Pagination and sorting configuration
     * @return Page of health statistics with issues
     */
    @Query("SELECT hs FROM WebhookHealthStats hs WHERE hs.healthStatus IN ('DEGRADED', 'UNHEALTHY')")
    Page<WebhookHealthStats> findWithHealthIssues(Pageable pageable);

    /**
     * Find webhooks with low success rates
     *
     * @param threshold Minimum success rate threshold
     * @param pageable Pagination and sorting configuration
     * @return Page of health statistics with low success rates
     */
    Page<WebhookHealthStats> findBySuccessRateLessThan(double threshold, Pageable pageable);

    /**
     * Find webhooks with high response times
     *
     * @param threshold Response time threshold in milliseconds
     * @param pageable Pagination and sorting configuration
     * @return Page of health statistics with high response times
     */
    Page<WebhookHealthStats> findByAverageResponseTimeGreaterThan(double threshold, Pageable pageable);

    /**
     * Update delivery statistics for a webhook
     *
     * @param webhookConfigId ID of the webhook configuration
     * @param delivered Number of delivered messages to add
     * @param failed Number of failed messages to add
     * @param responseTime Response time to include in average calculation
     * @return Number of updated records
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookHealthStats hs SET " +
            "hs.deliveredMessages = hs.deliveredMessages + :delivered, " +
            "hs.failedMessages = hs.failedMessages + :failed, " +
            "hs.totalAttempts = hs.totalAttempts + 1, " +
            "hs.averageResponseTime = (hs.averageResponseTime * hs.totalAttempts + :responseTime) / (hs.totalAttempts + 1), " +
            "hs.lastResponseTime = :responseTime, " +
            "hs.lastUpdated = CURRENT_TIMESTAMP " +
            "WHERE hs.webhookConfigId = :webhookConfigId")
    int updateDeliveryStats(
            @Param("webhookConfigId") UUID webhookConfigId,
            @Param("delivered") long delivered,
            @Param("failed") long failed,
            @Param("responseTime") long responseTime);

    /**
     * Update webhook health status
     *
     * @param webhookConfigId ID of the webhook configuration
     * @param status New health status
     * @return Number of updated records
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookHealthStats hs SET " +
            "hs.healthStatus = :status, " +
            "hs.lastStatusChange = CURRENT_TIMESTAMP, " +
            "hs.statusChangesLast24h = hs.statusChangesLast24h + 1, " +
            "hs.lastUpdated = CURRENT_TIMESTAMP " +
            "WHERE hs.webhookConfigId = :webhookConfigId AND hs.healthStatus != :status")
    int updateHealthStatus(
            @Param("webhookConfigId") UUID webhookConfigId,
            @Param("status") HealthStatus status);

    /**
     * Record a successful delivery for a webhook
     *
     * @param webhookConfigId ID of the webhook configuration
     * @param statusCode HTTP status code
     * @param responseTime Response time in milliseconds
     * @return Number of updated records
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookHealthStats hs SET " +
            "hs.deliveredMessages = hs.deliveredMessages + 1, " +
            "hs.totalAttempts = hs.totalAttempts + 1, " +
            "hs.successfulAttempts = hs.successfulAttempts + 1, " +
            "hs.successRate = (hs.successfulAttempts * 100.0) / hs.totalAttempts, " +
            "hs.averageResponseTime = (hs.averageResponseTime * hs.totalAttempts + :responseTime) / (hs.totalAttempts + 1), " +
            "hs.lastResponseTime = :responseTime, " +
            "hs.lastSuccessfulDelivery = CURRENT_TIMESTAMP, " +
            "hs.lastUpdated = CURRENT_TIMESTAMP " +
            "WHERE hs.webhookConfigId = :webhookConfigId")
    int recordSuccessfulDelivery(
            @Param("webhookConfigId") UUID webhookConfigId,
            @Param("statusCode") int statusCode,
            @Param("responseTime") long responseTime);

    /**
     * Record a failed delivery for a webhook
     *
     * @param webhookConfigId ID of the webhook configuration
     * @return Number of updated records
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookHealthStats hs SET " +
            "hs.failedMessages = hs.failedMessages + 1, " +
            "hs.totalAttempts = hs.totalAttempts + 1, " +
            "hs.successRate = (hs.successfulAttempts * 100.0) / hs.totalAttempts, " +
            "hs.lastFailure = CURRENT_TIMESTAMP, " +
            "hs.lastUpdated = CURRENT_TIMESTAMP " +
            "WHERE hs.webhookConfigId = :webhookConfigId")
    int recordFailedDelivery(@Param("webhookConfigId") UUID webhookConfigId);

    /**
     * Record a failed delivery attempt for a webhook
     *
     * @param webhookConfigId ID of the webhook configuration
     * @param statusCode HTTP status code (null for connection errors)
     * @param responseTime Response time in milliseconds
     * @return Number of updated records
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookHealthStats hs SET " +
            "hs.totalAttempts = hs.totalAttempts + 1, " +
            "hs.successRate = (hs.successfulAttempts * 100.0) / hs.totalAttempts, " +
            "hs.lastResponseTime = :responseTime, " +
            "hs.lastFailure = CURRENT_TIMESTAMP, " +
            "hs.mostCommonErrorCode = CASE WHEN :statusCode IS NOT NULL THEN :statusCode ELSE hs.mostCommonErrorCode END, " +
            "hs.lastUpdated = CURRENT_TIMESTAMP " +
            "WHERE hs.webhookConfigId = :webhookConfigId")
    int recordFailedAttempt(
            @Param("webhookConfigId") UUID webhookConfigId,
            @Param("statusCode") Integer statusCode,
            @Param("responseTime") long responseTime);

    /**
     * Record a connection error for a webhook
     *
     * @param webhookConfigId ID of the webhook configuration
     * @return Number of updated records
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookHealthStats hs SET " +
            "hs.totalAttempts = hs.totalAttempts + 1, " +
            "hs.successRate = (hs.successfulAttempts * 100.0) / hs.totalAttempts, " +
            "hs.lastFailure = CURRENT_TIMESTAMP, " +
            "hs.lastUpdated = CURRENT_TIMESTAMP " +
            "WHERE hs.webhookConfigId = :webhookConfigId")
    int recordConnectionError(@Param("webhookConfigId") UUID webhookConfigId);

    /**
     * Find webhooks that need a health status update based on success rate
     *
     * @return List of health statistics that need updating
     */
    @Query("SELECT hs FROM WebhookHealthStats hs WHERE " +
            "(hs.successRate < 75 AND hs.healthStatus = 'HEALTHY') OR " +
            "(hs.successRate < 50 AND hs.healthStatus = 'DEGRADED') OR " +
            "(hs.successRate > 95 AND hs.healthStatus = 'UNHEALTHY') OR " +
            "(hs.successRate > 75 AND hs.healthStatus = 'UNHEALTHY') OR " +
            "(hs.totalAttempts > 5 AND hs.healthStatus = 'UNKNOWN')")
    List<WebhookHealthStats> findHealthStatsNeedingUpdate();

    /**
     * Reset status change counter for health stats older than 24 hours
     *
     * @param threshold Threshold time (24 hours ago)
     * @return Number of updated records
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookHealthStats hs SET " +
            "hs.statusChangesLast24h = 0 " +
            "WHERE hs.lastStatusChange < :threshold")
    int resetStatusChangeCounter(@Param("threshold") OffsetDateTime threshold);

    /**
     * Get combined health statistics for all webhooks
     *
     * @return List containing aggregated statistics
     */
    @Query("SELECT " +
            "SUM(hs.totalMessages) as totalMessages, " +
            "SUM(hs.deliveredMessages) as deliveredMessages, " +
            "SUM(hs.failedMessages) as failedMessages, " +
            "SUM(hs.pendingMessages) as pendingMessages, " +
            "AVG(hs.successRate) as avgSuccessRate, " +
            "AVG(hs.averageResponseTime) as avgResponseTime, " +
            "COUNT(CASE WHEN hs.healthStatus = 'HEALTHY' THEN 1 END) as healthyCount, " +
            "COUNT(CASE WHEN hs.healthStatus = 'DEGRADED' THEN 1 END) as degradedCount, " +
            "COUNT(CASE WHEN hs.healthStatus = 'UNHEALTHY' THEN 1 END) as unhealthyCount, " +
            "COUNT(CASE WHEN hs.healthStatus = 'UNKNOWN' THEN 1 END) as unknownCount, " +
            "COUNT(CASE WHEN hs.healthStatus = 'INACTIVE' THEN 1 END) as inactiveCount " +
            "FROM WebhookHealthStats hs")
    List<Object[]> getAggregatedStats();

    /**
     * Get webhooks with the most failures
     *
     * @param limit Maximum number of results
     * @return List of webhook names and failure counts
     */
    @Query("SELECT hs.webhookName, hs.failedMessages " +
            "FROM WebhookHealthStats hs " +
            "ORDER BY hs.failedMessages DESC")
    List<Object[]> getWebhooksWithMostFailures(@Param("limit") int limit);

    /**
     * Update uptime percentage for webhooks
     *
     * @param incrementHealthy Increment for healthy webhooks
     * @param decrementUnhealthy Decrement for unhealthy webhooks
     * @return Number of updated records
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookHealthStats hs SET " +
            "hs.uptime = CASE " +
            "  WHEN hs.healthStatus = 'HEALTHY' THEN LEAST(100, hs.uptime + :incrementHealthy) " +
            "  WHEN hs.healthStatus = 'UNHEALTHY' THEN GREATEST(0, hs.uptime - :decrementUnhealthy) " +
            "  ELSE hs.uptime " +
            "END")
    int updateUptimePercentages(
            @Param("incrementHealthy") double incrementHealthy,
            @Param("decrementUnhealthy") double decrementUnhealthy);

    /**
     * Create a new health stats record for a webhook configuration
     *
     * @param webhookConfigId ID of the webhook configuration
     * @param webhookName Name of the webhook
     * @return The created health stats
     */
    @Modifying
    @Transactional
    @Query(value =
            "INSERT INTO webhook_health_stats (id, webhook_config_id, webhook_name, " +
                    "total_messages, delivered_messages, failed_messages, pending_messages, " +
                    "total_attempts, successful_attempts, average_response_time, success_rate, " +
                    "health_status, last_health_check, last_status_change, uptime, status_changes_last_24h, " +
                    "stats_timeframe, last_updated) " +
                    "VALUES (gen_random_uuid(), :webhookConfigId, :webhookName, " +
                    "0, 0, 0, 0, 0, 0, 0, 100, 'UNKNOWN', CURRENT_TIMESTAMP, " +
                    "CURRENT_TIMESTAMP, 100, 0, 'ALL', CURRENT_TIMESTAMP) " +
                    "RETURNING id",
            nativeQuery = true)
    UUID createInitialHealthStats(
            @Param("webhookConfigId") UUID webhookConfigId,
            @Param("webhookName") String webhookName);
}