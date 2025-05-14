package com.sg.webhookservice.repository;

import com.sg.webhookservice.model.Message;
import com.sg.webhookservice.model.Message.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio para acceder y gestionar entidades Message.
 *
 * Proporciona métodos para buscar, filtrar y procesar mensajes de webhook,
 * así como operaciones para mantenimiento y análisis estadístico.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, UUID>,
        JpaSpecificationExecutor<Message>,
        MessageRepositoryExtension {

    /**
     * Encuentra mensajes por su estado
     *
     * @param status Estado a buscar
     * @param pageable Configuración de paginación
     * @return Página de mensajes con ese estado
     */
    Page<Message> findByStatus(MessageStatus status, Pageable pageable);

    /**
     * Encuentra mensajes para una configuración de webhook específica
     *
     * @param webhookConfigId ID de la configuración
     * @param pageable Configuración de paginación
     * @return Página de mensajes
     */
    Page<Message> findByWebhookConfigId(UUID webhookConfigId, Pageable pageable);

    /**
     * Encuentra mensajes por nombre de webhook
     *
     * @param webhookName Nombre del webhook
     * @param pageable Configuración de paginación
     * @return Página de mensajes
     */
    @Query("SELECT m FROM Message m JOIN m.webhookConfig wc WHERE wc.name = :webhookName")
    Page<Message> findByWebhookName(@Param("webhookName") String webhookName, Pageable pageable);

    /**
     * Encuentra mensajes fallidos que están listos para reintento
     *
     * @param now Tiempo actual
     * @param limit Límite de resultados
     * @return Lista de mensajes listos para reintento
     */
    @Query("SELECT m FROM Message m " +
            "WHERE m.status = 'FAILED' AND m.nextRetry IS NOT NULL AND m.nextRetry <= :now " +
            "ORDER BY m.nextRetry ASC")
    List<Message> findMessagesReadyForRetry(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    /**
     * Encuentra mensajes pendientes
     *
     * @param limit Límite de resultados
     * @return Lista de mensajes pendientes
     */
    @Query("SELECT m FROM Message m WHERE m.status = 'PENDING' ORDER BY m.createdAt ASC")
    List<Message> findPendingMessages(@Param("limit") int limit);

    /**
     * Encuentra mensajes en procesamiento que llevan demasiado tiempo (posibles cuelgues)
     *
     * @param threshold Tiempo límite para considerar colgado
     * @return Lista de mensajes potencialmente colgados
     */
    @Query("SELECT m FROM Message m " +
            "WHERE m.status = 'PROCESSING' AND m.updatedAt < :threshold")
    List<Message> findStuckMessages(@Param("threshold") OffsetDateTime threshold);

    /**
     * Actualiza el estado de un mensaje
     *
     * @param messageId ID del mensaje
     * @param status Nuevo estado
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = :status, m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :messageId")
    int updateMessageStatus(@Param("messageId") UUID messageId, @Param("status") MessageStatus status);

    /**
     * Marca un mensaje como en procesamiento
     *
     * @param messageId ID del mensaje
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = 'PROCESSING', m.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE m.id = :messageId AND m.status IN ('PENDING', 'FAILED')")
    int markAsProcessing(@Param("messageId") UUID messageId);

    /**
     * Marca un mensaje como entregado
     *
     * @param messageId ID del mensaje
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = 'DELIVERED', m.nextRetry = NULL, " +
            "m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :messageId")
    int markAsDelivered(@Param("messageId") UUID messageId);

    /**
     * Marca un mensaje como fallido y programa el próximo reintento
     *
     * @param messageId ID del mensaje
     * @param error Mensaje de error
     * @param nextRetry Tiempo para próximo reintento
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = 'FAILED', m.lastError = :error, " +
            "m.nextRetry = :nextRetry, m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :messageId")
    int markAsFailed(
            @Param("messageId") UUID messageId,
            @Param("error") String error,
            @Param("nextRetry") OffsetDateTime nextRetry);

    /**
     * Cancela un mensaje pendiente o fallido
     *
     * @param messageId ID del mensaje
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.status = 'CANCELLED', m.nextRetry = NULL, " +
            "m.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE m.id = :messageId AND m.status IN ('PENDING', 'FAILED')")
    int cancelMessage(@Param("messageId") UUID messageId);

    /**
     * Incrementa el contador de reintentos para un mensaje
     *
     * @param messageId ID del mensaje
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.retryCount = m.retryCount + 1, " +
            "m.updatedAt = CURRENT_TIMESTAMP WHERE m.id = :messageId")
    int incrementRetryCount(@Param("messageId") UUID messageId);

    /**
     * Borra mensajes antiguos según criterios específicos
     *
     * @param cutoffTime Tiempo límite para considerar antiguos
     * @param statuses Estados elegibles para borrado
     * @return Número de registros eliminados
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Message m WHERE m.createdAt < :cutoffTime AND m.status IN :statuses")
    int deleteOldMessages(
            @Param("cutoffTime") OffsetDateTime cutoffTime,
            @Param("statuses") List<MessageStatus> statuses);

    /**
     * Busca mensajes con filtros combinados
     *
     * @param webhookName Nombre del webhook (opcional)
     * @param status Estado (opcional)
     * @param startDate Fecha de inicio (opcional)
     * @param endDate Fecha de fin (opcional)
     * @param pageable Configuración de paginación
     * @return Página de mensajes
     */
    @Query("SELECT m FROM Message m JOIN m.webhookConfig wc " +
            "WHERE (:webhookName IS NULL OR wc.name = :webhookName) " +
            "AND (:status IS NULL OR m.status = :status) " +
            "AND (:startDate IS NULL OR m.createdAt >= :startDate) " +
            "AND (:endDate IS NULL OR m.createdAt <= :endDate)")
    Page<Message> searchMessages(
            @Param("webhookName") String webhookName,
            @Param("status") MessageStatus status,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    /**
     * Find a message by ID with all delivery attempts
     *
     * @param messageId Message ID
     * @return The message with delivery attempts if found
     */
    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.deliveryAttempts WHERE m.id = :messageId")
    Optional<Message> findByIdWithDeliveryAttempts(@Param("messageId") UUID messageId);
}