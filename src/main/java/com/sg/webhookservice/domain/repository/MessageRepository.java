package com.sg.webhookservice.infrastructure.repository;

import com.sg.webhookservice.domain.entity.Message;
import com.sg.webhookservice.domain.entity.WebhookConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repositorio para la entidad Message.
 * Proporciona métodos para realizar operaciones CRUD y búsquedas personalizadas.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Busca mensajes por el ID de configuración de webhook asociada.
     *
     * @param webhookConfigId ID de la configuración
     * @return Lista de mensajes
     */
    List<Message> findByWebhookConfigId(UUID webhookConfigId);

    /**
     * Busca mensajes por el ID de configuración de webhook y estado.
     *
     * @param webhookConfigId ID de la configuración
     * @param status Estado del mensaje
     * @return Lista de mensajes
     */
    List<Message> findByWebhookConfigIdAndStatus(UUID webhookConfigId, Message.MessageStatus status);

    /**
     * Busca mensajes por estado.
     *
     * @param status Estado del mensaje
     * @return Lista de mensajes
     */
    List<Message> findByStatus(Message.MessageStatus status);

    /**
     * Busca mensajes por estado como String.
     *
     * @param status Estado del mensaje como String
     * @return Lista de mensajes
     */
    @Query("SELECT m FROM Message m WHERE m.status = :status")
    List<Message> findByStatus(@Param("status") String status);

    /**
     * Obtiene un mensaje por ID con todos sus intentos de entrega cargados.
     *
     * @param id ID del mensaje
     * @return Mensaje con intentos de entrega o Optional vacío
     */
    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.deliveryAttempts WHERE m.id = :id")
    Optional<Message> findByIdWithDeliveryAttempts(@Param("id") UUID id);

    /**
     * Marca un mensaje como entregado.
     *
     * @param id ID del mensaje
     * @return Número de filas afectadas
     */
    @Modifying
    @Query("UPDATE Message m SET m.status = 'DELIVERED', m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    int markAsDelivered(@Param("id") UUID id);

    /**
     * Marca un mensaje como en procesamiento.
     *
     * @param id ID del mensaje
     * @return Número de filas afectadas
     */
    @Modifying
    @Query("UPDATE Message m SET m.status = 'PROCESSING', m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    int markAsProcessing(@Param("id") UUID id);

    /**
     * Marca un mensaje como fallido.
     *
     * @param id ID del mensaje
     * @param error Mensaje de error
     * @param nextRetry Próximo tiempo de reintento (puede ser null)
     * @return Número de filas afectadas
     */
    @Modifying
    @Query("UPDATE Message m SET m.status = 'FAILED', m.lastError = :error, " +
            "m.nextRetry = :nextRetry, m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    int markAsFailed(@Param("id") UUID id, @Param("error") String error, @Param("nextRetry") OffsetDateTime nextRetry);

    /**
     * Incrementa el contador de reintentos.
     *
     * @param id ID del mensaje
     * @return Número de filas afectadas
     */
    @Modifying
    @Query("UPDATE Message m SET m.retryCount = m.retryCount + 1, m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    int incrementRetryCount(@Param("id") UUID id);

    /**
     * Cancela un mensaje.
     *
     * @param id ID del mensaje
     * @return Número de filas afectadas
     */
    @Modifying
    @Query("UPDATE Message m SET m.status = 'CANCELLED', m.nextRetry = NULL, " +
            "m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :id")
    int cancelMessage(@Param("id") UUID id);

    /**
     * Encuentra mensajes pendientes de reintento.
     *
     * @param now Tiempo actual
     * @return Lista de mensajes listos para reintento
     */
    @Query("SELECT m FROM Message m WHERE m.status = 'FAILED' AND m.nextRetry <= :now")
    List<Message> findMessagesForRetry(@Param("now") OffsetDateTime now);

    /**
     * Cuenta mensajes en estado fallido con próximo reintento antes de una fecha.
     *
     * @param status Estado del mensaje
     * @param nextRetry Fecha límite para próximo reintento
     * @return Número de mensajes
     */
    long countByStatusAndNextRetryBefore(String status, OffsetDateTime nextRetry);

    /**
     * Encuentra mensajes fallidos actualizados después de una fecha.
     *
     * @param cutoffTime Fecha límite
     * @param pageable Configuración de paginación
     * @return Página de mensajes
     */
    @Query("SELECT m FROM Message m WHERE m.status = 'FAILED' AND m.updatedAt >= :cutoffTime")
    Page<Message> findFailedMessagesUpdatedAfter(@Param("cutoffTime") OffsetDateTime cutoffTime, Pageable pageable);

    /**
     * Encuentra mensajes expirados.
     *
     * @param cutoffTime Fecha límite
     * @param pageable Configuración de paginación
     * @return Página de mensajes
     */
    @Query("SELECT m FROM Message m WHERE m.status IN ('PENDING', 'FAILED') " +
            "AND m.createdAt < :cutoffTime")
    Page<Message> findExpiredMessages(@Param("cutoffTime") OffsetDateTime cutoffTime, Pageable pageable);

    /**
     * Busca mensajes con múltiples criterios.
     *
     * @param webhookName Nombre del webhook (opcional)
     * @param status Estado del mensaje (opcional)
     * @param fromDate Fecha desde (opcional)
     * @param toDate Fecha hasta (opcional)
     * @param pageable Configuración de paginación
     * @return Página de mensajes que coinciden con los criterios
     */
    @Query("SELECT m FROM Message m JOIN m.webhookConfig w WHERE " +
            "(:webhookName IS NULL OR w.name = :webhookName) AND " +
            "(:status IS NULL OR m.status = :status) AND " +
            "(:fromDate IS NULL OR m.createdAt >= :fromDate) AND " +
            "(:toDate IS NULL OR m.createdAt <= :toDate) " +
            "ORDER BY m.createdAt DESC")
    Page<Message> searchMessages(
            @Param("webhookName") String webhookName,
            @Param("status") Message.MessageStatus status,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);
}
