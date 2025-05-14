package com.sg.webhookservice.repository;

import com.sg.webhookservice.model.DeliveryAttempt;
import com.yourcompany.webhookservice.model.DeliveryAttempt;
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
import java.util.Map;
import java.util.UUID;

/**
 * Repositorio para acceder y gestionar entidades DeliveryAttempt.
 * 
 * Proporciona métodos para buscar, filtrar y analizar intentos de entrega
 * de webhooks, así como operaciones de mantenimiento.
 */
@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    
    /**
     * Encuentra todos los intentos de entrega para un mensaje específico, ordenados por número de intento
     * 
     * @param messageId ID del mensaje
     * @return Lista ordenada de intentos de entrega
     */
    List<DeliveryAttempt> findByMessageIdOrderByAttemptNumberAsc(UUID messageId);
    
    /**
     * Encuentra el último intento de entrega para un mensaje específico
     * 
     * @param messageId ID del mensaje
     * @return El intento de entrega más reciente o null si no hay intentos
     */
    @Query("SELECT da FROM DeliveryAttempt da WHERE da.message.id = :messageId " +
           "ORDER BY da.attemptNumber DESC LIMIT 1")
    DeliveryAttempt findLatestByMessageId(@Param("messageId") UUID messageId);
    
    /**
     * Encuentra intentos de entrega filtrados por código de estado HTTP
     * 
     * @param statusCode Código de estado HTTP
     * @param pageable Configuración de paginación
     * @return Página de intentos de entrega
     */
    Page<DeliveryAttempt> findByStatusCode(Integer statusCode, Pageable pageable);
    
    /**
     * Encuentra intentos de entrega con errores (sin código de estado)
     * 
     * @param pageable Configuración de paginación
     * @return Página de intentos de entrega con errores
     */
    @Query("SELECT da FROM DeliveryAttempt da WHERE da.statusCode IS NULL AND da.error IS NOT NULL")
    Page<DeliveryAttempt> findWithConnectionErrors(Pageable pageable);
    
    /**
     * Encuentra intentos de entrega lentos
     * 
     * @param minDuration Duración mínima en milisegundos
     * @param pageable Configuración de paginación
     * @return Página de intentos lentos
     */
    Page<DeliveryAttempt> findByRequestDurationGreaterThanEqual(Long minDuration, Pageable pageable);
    
    /**
     * Cuenta intentos de entrega agrupados por código de estado
     * 
     * @param webhookConfigId ID de la configuración de webhook (opcional)
     * @param startTime Tiempo de inicio para el filtro (opcional)
     * @return Mapa con conteo por código de estado
     */
    @Query("SELECT da.statusCode, COUNT(da) FROM DeliveryAttempt da " +
           "WHERE (:webhookConfigId IS NULL OR da.message.webhookConfig.id = :webhookConfigId) " +
           "AND (:startTime IS NULL OR da.timestamp >= :startTime) " +
           "GROUP BY da.statusCode ORDER BY COUNT(da) DESC")
    List<Object[]> countByStatusCode(
            @Param("webhookConfigId") UUID webhookConfigId,
            @Param("startTime") OffsetDateTime startTime);
    
    /**
     * Calcula el tiempo de respuesta promedio por webhook
     * 
     * @param startTime Tiempo de inicio para el filtro
     * @return Lista de resultados [webhookName, avgTime]
     */
    @Query("SELECT wc.name, AVG(da.requestDuration) FROM DeliveryAttempt da " +
           "JOIN da.message m JOIN m.webhookConfig wc " +
           "WHERE da.timestamp >= :startTime AND da.requestDuration IS NOT NULL " +
           "GROUP BY wc.name ORDER BY AVG(da.requestDuration) DESC")
    List<Object[]> calculateAverageResponseTimeByWebhook(@Param("startTime") OffsetDateTime startTime);
    
    /**
     * Encuentra intentos de entrega recientes para un webhook específico
     * 
     * @param webhookName Nombre del webhook
     * @param limit Número máximo de resultados
     * @return Lista de intentos recientes
     */
    @Query("SELECT da FROM DeliveryAttempt da JOIN da.message m JOIN m.webhookConfig wc " +
           "WHERE wc.name = :webhookName ORDER BY da.timestamp DESC LIMIT :limit")
    List<DeliveryAttempt> findRecentByWebhookName(
            @Param("webhookName") String webhookName, 
            @Param("limit") int limit);
    
    /**
     * Calcula la tasa de éxito por webhook en un período
     * 
     * @param startTime Tiempo de inicio para el filtro
     * @return Lista de resultados [webhookName, totalAttempts, successfulAttempts, successRate]
     */
    @Query("SELECT wc.name, " +
           "COUNT(da) as total, " +
           "SUM(CASE WHEN da.statusCode >= 200 AND da.statusCode < 300 THEN 1 ELSE 0 END) as success, " +
           "SUM(CASE WHEN da.statusCode >= 200 AND da.statusCode < 300 THEN 1 ELSE 0 END) * 100.0 / COUNT(da) as rate " +
           "FROM DeliveryAttempt da JOIN da.message m JOIN m.webhookConfig wc " +
           "WHERE da.timestamp >= :startTime " +
           "GROUP BY wc.name ORDER BY rate DESC")
    List<Object[]> calculateSuccessRateByWebhook(@Param("startTime") OffsetDateTime startTime);
    
    /**
     * Busca intentos con patrones de error específicos
     * 
     * @param errorPattern Patrón de texto para buscar en mensajes de error
     * @param pageable Configuración de paginación
     * @return Página de intentos con ese patrón de error
     */
    @Query("SELECT da FROM DeliveryAttempt da WHERE da.error LIKE %:errorPattern%")
    Page<DeliveryAttempt> findByErrorContaining(
            @Param("errorPattern") String errorPattern, 
            Pageable pageable);
    
    /**
     * Borra intentos de entrega antiguos para un webhook específico
     * 
     * @param webhookConfigId ID de la configuración de webhook
     * @param cutoffTime Tiempo límite para considerar antiguos
     * @return Número de registros eliminados
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM DeliveryAttempt da WHERE da.message.webhookConfig.id = :webhookConfigId " +
           "AND da.timestamp < :cutoffTime")
    int deleteOldAttemptsByWebhookConfig(
            @Param("webhookConfigId") UUID webhookConfigId,
            @Param("cutoffTime") OffsetDateTime cutoffTime);
    
    /**
     * Borra intentos de entrega antiguos para mensajes entregados exitosamente
     * 
     * @param cutoffDays Número de días para considerar antiguos
     * @return Número de registros eliminados
     */
    @Modifying
    @Transactional
    @Query(value = 
           "DELETE FROM delivery_attempts da USING messages m " +
           "WHERE da.message_id = m.id AND m.status = 'DELIVERED' " +
           "AND da.timestamp < now() - INTERVAL ':cutoffDays days'", 
           nativeQuery = true)
    int cleanupOldDeliveryAttempts(@Param("cutoffDays") int cutoffDays);
    
    /**
     * Encuentra patrones de error comunes agrupados por mensaje
     * 
     * @param startTime Tiempo de inicio para el filtro
     * @return Lista de errores comunes con conteo
     */
    @Query("SELECT SUBSTRING(da.error, 1, 100) as errorMsg, COUNT(da) as count " +
           "FROM DeliveryAttempt da " +
           "WHERE da.timestamp >= :startTime AND da.error IS NOT NULL " +
           "GROUP BY errorMsg ORDER BY count DESC")
    List<Object[]> findCommonErrors(@Param("startTime") OffsetDateTime startTime);
    
    /**
     * Encuentra nodos con problemas basado en tasas de error
     * 
     * @param minErrorRate Tasa mínima de error para considerar problemático (0-100)
     * @param minAttempts Número mínimo de intentos para considerar
     * @return Lista de nodos con problemas [nodeName, totalAttempts, failureRate]
     */
    @Query("SELECT da.processingNode, COUNT(da) as total, " +
           "(1 - SUM(CASE WHEN da.statusCode >= 200 AND da.statusCode < 300 THEN 1 ELSE 0 END) * 1.0 / COUNT(da)) * 100 as errorRate " +
           "FROM DeliveryAttempt da " +
           "WHERE da.processingNode IS NOT NULL " +
           "GROUP BY da.processingNode " +
           "HAVING COUNT(da) >= :minAttempts AND " +
           "(1 - SUM(CASE WHEN da.statusCode >= 200 AND da.statusCode < 300 THEN 1 ELSE 0 END) * 1.0 / COUNT(da)) * 100 >= :minErrorRate " +
           "ORDER BY errorRate DESC")
    List<Object[]> findProblematicNodes(
            @Param("minErrorRate") double minErrorRate,
            @Param("minAttempts") int minAttempts);
}