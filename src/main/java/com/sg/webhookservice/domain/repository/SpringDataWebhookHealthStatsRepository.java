package com.sg.webhookservice.domain.repository;

import com.sg.webhookservice.domain.entity.WebhookHealthStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la entidad WebhookHealthStats.
 * Proporciona métodos para realizar operaciones CRUD y actualizaciones de estadísticas.
 */
@Repository
public interface SpringDataWebhookHealthStatsRepository extends JpaRepository<WebhookHealthStats, UUID> {

    /**
     * Busca estadísticas por el ID de configuración de webhook.
     *
     * @param webhookConfigId ID de la configuración
     * @return Estadísticas encontradas o Optional vacío
     */
    Optional<WebhookHealthStats> findByWebhookConfigId(UUID webhookConfigId);

    /**
     * Busca estadísticas por el nombre del webhook.
     *
     * @param webhookName Nombre del webhook
     * @return Estadísticas encontradas o Optional vacío
     */
    Optional<WebhookHealthStats> findByWebhookName(String webhookName);

    /**
     * Obtiene todas las estadísticas ordenadas por tasa de éxito.
     *
     * @return Lista de estadísticas
     */
    @Query("SELECT s FROM WebhookHealthStats s ORDER BY " +
            "CASE WHEN s.totalSent = 0 THEN 100 ELSE (s.totalDelivered * 100.0 / s.totalSent) END DESC")
    List<WebhookHealthStats> findAllOrderBySuccessRate();

    /**
     * Registra una entrega exitosa.
     *
     * @param webhookConfigId ID de la configuración de webhook
     * @param responseTimeMs Tiempo de respuesta en milisegundos
     * @return Número de filas afectadas
     */
    @Modifying
    @Query("UPDATE WebhookHealthStats s SET " +
            "s.totalSent = s.totalSent + 1, " +
            "s.totalDelivered = s.totalDelivered + 1, " +
            "s.lastSuccessTime = CURRENT_TIMESTAMP, " +
            "s.avgResponseTime = CASE WHEN s.avgResponseTime = 0 THEN :responseTimeMs " +
            "ELSE (s.avgResponseTime * 0.7 + :responseTimeMs * 0.3) END, " +
            "s.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE s.webhookConfigId = :webhookConfigId")
    int recordSuccessfulDelivery(@Param("webhookConfigId") UUID webhookConfigId,
                                 @Param("responseTimeMs") long responseTimeMs);

    /**
     * Registra una entrega fallida.
     *
     * @param webhookConfigId ID de la configuración de webhook
     * @param errorMessage Mensaje de error
     * @return Número de filas afectadas
     */
    @Modifying
    @Query("UPDATE WebhookHealthStats s SET " +
            "s.totalSent = s.totalSent + 1, " +
            "s.totalFailed = s.totalFailed + 1, " +
            "s.lastErrorTime = CURRENT_TIMESTAMP, " +
            "s.lastError = :errorMessage, " +
            "s.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE s.webhookConfigId = :webhookConfigId")
    int recordFailedDelivery(@Param("webhookConfigId") UUID webhookConfigId,
                             @Param("errorMessage") String errorMessage);

    /**
     * Actualiza el nombre del webhook en las estadísticas cuando cambia en la configuración.
     *
     * @param webhookConfigId ID de la configuración de webhook
     * @param newName Nuevo nombre
     * @return Número de filas afectadas
     */
    @Modifying
    @Query("UPDATE WebhookHealthStats s SET s.webhookName = :newName, " +
            "s.updatedAt = CURRENT_TIMESTAMP WHERE s.webhookConfigId = :webhookConfigId")
    int updateWebhookName(@Param("webhookConfigId") UUID webhookConfigId, @Param("newName") String newName);

    /**
     * Encuentra webhooks con problemas de salud (baja tasa de éxito).
     *
     * @param minimumSent Mínimo de mensajes enviados para considerar
     * @param successRateThreshold Umbral de tasa de éxito
     * @return Lista de estadísticas de webhooks con problemas
     */
    @Query("SELECT s FROM WebhookHealthStats s WHERE s.totalSent >= :minimumSent " +
            "AND (s.totalDelivered * 100.0 / s.totalSent) < :successRateThreshold " +
            "ORDER BY (s.totalDelivered * 100.0 / s.totalSent) ASC")
    List<WebhookHealthStats> findUnhealthyWebhooks(@Param("minimumSent") int minimumSent,
                                                   @Param("successRateThreshold") double successRateThreshold);

    /**
     * Obtiene estadísticas resumidas para todos los webhooks.
     *
     * @return Objeto con totales globales
     */
    @Query("SELECT SUM(s.totalSent) as totalSent, SUM(s.totalDelivered) as totalDelivered, " +
            "SUM(s.totalFailed) as totalFailed, AVG(s.avgResponseTime) as avgResponseTime " +
            "FROM WebhookHealthStats s")
    Object[] getGlobalStats();

    /**
     * Encuentra webhooks inactivos (sin actividad reciente).
     *
     * @param cutoffTime Tiempo límite de inactividad
     * @return Lista de estadísticas de webhooks inactivos
     */
    @Query("SELECT s FROM WebhookHealthStats s WHERE s.updatedAt < :cutoffTime")
    List<WebhookHealthStats> findInactiveWebhooks(@Param("cutoffTime") OffsetDateTime cutoffTime);
}