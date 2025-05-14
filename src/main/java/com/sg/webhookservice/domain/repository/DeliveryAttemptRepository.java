package com.sg.webhookservice.domain.repository;

import com.sg.webhookservice.domain.entity.DeliveryAttempt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repositorio para la entidad DeliveryAttempt.
 * Proporciona métodos para realizar operaciones CRUD y búsquedas personalizadas.
 */
@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    /**
     * Busca intentos de entrega por el ID del mensaje asociado.
     *
     * @param messageId ID del mensaje
     * @return Lista de intentos ordenados por número de intento
     */
    @Query("SELECT d FROM DeliveryAttempt d WHERE d.message.id = :messageId ORDER BY d.attemptNumber ASC")
    List<DeliveryAttempt> findByMessageId(@Param("messageId") UUID messageId);

    /**
     * Busca el último intento de entrega para un mensaje.
     *
     * @param messageId ID del mensaje
     * @return Último intento o null si no hay intentos
     */
    @Query("SELECT d FROM DeliveryAttempt d WHERE d.message.id = :messageId " +
            "ORDER BY d.attemptNumber DESC")
    DeliveryAttempt findLastAttemptByMessageId(@Param("messageId") UUID messageId);

    /**
     * Encuentra intentos de entrega exitosos para un mensaje.
     *
     * @param messageId ID del mensaje
     * @return Lista de intentos exitosos
     */
    @Query("SELECT d FROM DeliveryAttempt d WHERE d.message.id = :messageId " +
            "AND d.statusCode >= 200 AND d.statusCode < 300")
    List<DeliveryAttempt> findSuccessfulAttemptsByMessageId(@Param("messageId") UUID messageId);

    /**
     * Encuentra intentos de entrega fallidos para un mensaje.
     *
     * @param messageId ID del mensaje
     * @return Lista de intentos fallidos
     */
    @Query("SELECT d FROM DeliveryAttempt d WHERE d.message.id = :messageId " +
            "AND (d.statusCode IS NULL OR d.statusCode < 200 OR d.statusCode >= 300)")
    List<DeliveryAttempt> findFailedAttemptsByMessageId(@Param("messageId") UUID messageId);

    /**
     * Cuenta el número de intentos para un mensaje.
     *
     * @param messageId ID del mensaje
     * @return Número de intentos
     */
    @Query("SELECT COUNT(d) FROM DeliveryAttempt d WHERE d.message.id = :messageId")
    long countByMessageId(@Param("messageId") UUID messageId);

    /**
     * Calcula el tiempo de respuesta promedio para los intentos de un webhook.
     *
     * @param webhookConfigId ID de la configuración de webhook
     * @return Tiempo promedio en milisegundos o null si no hay datos
     */
    @Query("SELECT AVG(d.requestDuration) FROM DeliveryAttempt d " +
            "JOIN d.message m WHERE m.webhookConfig.id = :webhookConfigId " +
            "AND d.requestDuration IS NOT NULL")
    Double getAverageResponseTimeByWebhookConfigId(@Param("webhookConfigId") UUID webhookConfigId);

    /**
     * Encuentra intentos de entrega antiguos para limpieza.
     *
     * @param cutoffDate Fecha límite
     * @return Lista de IDs de intentos antiguos
     */
    @Query("SELECT d.id FROM DeliveryAttempt d WHERE d.timestamp < :cutoffDate")
    List<UUID> findOldAttemptsForCleanup(@Param("cutoffDate") OffsetDateTime cutoffDate);

    /**
     * Encuentra intentos de entrega recientes para un webhook.
     *
     * @param webhookConfigId ID de la configuración de webhook
     * @return Lista de intentos recientes
     */
    @Query("SELECT d FROM DeliveryAttempt d JOIN d.message m " +
            "WHERE m.webhookConfig.id = :webhookConfigId " +
            "ORDER BY d.timestamp DESC")
    List<DeliveryAttempt> findRecentAttemptsByWebhookConfigId(
            @Param("webhookConfigId") UUID webhookConfigId, Pageable pageable);

    /**
     * Obtiene la distribución de códigos de estado para un webhook.
     *
     * @param webhookConfigId ID de la configuración de webhook
     * @return Lista de objetos con código de estado y conteo
     */
    @Query("SELECT d.statusCode as code, COUNT(d) as count FROM DeliveryAttempt d " +
            "JOIN d.message m WHERE m.webhookConfig.id = :webhookConfigId " +
            "AND d.statusCode IS NOT NULL GROUP BY d.statusCode ORDER BY count DESC")
    List<Object[]> getStatusCodeDistributionByWebhookConfigId(@Param("webhookConfigId") UUID webhookConfigId);
}