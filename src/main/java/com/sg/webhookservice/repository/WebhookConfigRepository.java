package com.sg.webhookservice.repository;

import com.sg.webhookservice.model.WebhookConfig;
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
 * Repositorio para acceder y gestionar entidades WebhookConfig.
 *
 * Proporciona métodos para buscar, filtrar y administrar configuraciones de webhook,
 * así como operaciones relacionadas con su ciclo de vida y estadísticas.
 */
@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, UUID> {

    /**
     * Encuentra una configuración de webhook por su nombre
     *
     * @param name Nombre único del webhook
     * @return Configuración opcional (puede no existir)
     */
    Optional<WebhookConfig> findByName(String name);

    /**
     * Encuentra configuraciones activas
     *
     * @return Lista de configuraciones activas
     */
    List<WebhookConfig> findByActiveTrue();

    /**
     * Encuentra configuraciones activas paginadas
     *
     * @param pageable Configuración de paginación
     * @return Página de configuraciones activas
     */
    Page<WebhookConfig> findByActiveTrue(Pageable pageable);

    /**
     * Encuentra configuración por nombre y estado de activación
     *
     * @param name Nombre del webhook
     * @param active Estado de activación
     * @return Configuración opcional
     */
    Optional<WebhookConfig> findByNameAndActive(String name, boolean active);

    /**
     * Encuentra configuración activa por nombre
     *
     * @param name Nombre del webhook
     * @return Configuración opcional
     */
    Optional<WebhookConfig> findByNameAndActiveTrue(String name);

    /**
     * Busca configuraciones activas con nombre que contiene el texto
     *
     * @param namePattern Patrón de texto en el nombre
     * @param pageable Configuración de paginación
     * @return Página de configuraciones coincidentes
     */
    @Query("SELECT wc FROM WebhookConfig wc WHERE wc.active = true AND wc.name LIKE %:namePattern%")
    Page<WebhookConfig> findActiveByNameContaining(@Param("namePattern") String namePattern, Pageable pageable);

    /**
     * Encuentra configuraciones que utilizan un patrón de URL específico
     *
     * @param urlPattern Patrón de URL a buscar
     * @param pageable Configuración de paginación
     * @return Página de configuraciones coincidentes
     */
    @Query("SELECT wc FROM WebhookConfig wc WHERE wc.targetUrl LIKE %:urlPattern%")
    Page<WebhookConfig> findByTargetUrlContaining(@Param("urlPattern") String urlPattern, Pageable pageable);

    /**
     * Cuenta configuraciones agrupadas por estrategia de backoff
     *
     * @return Lista de [estrategia, conteo]
     */
    @Query("SELECT wc.backoffStrategy, COUNT(wc) FROM WebhookConfig wc GROUP BY wc.backoffStrategy")
    List<Object[]> countByBackoffStrategy();

    /**
     * Busca configuraciones por grupo
     *
     * @param group Nombre del grupo
     * @param pageable Configuración de paginación
     * @return Página de configuraciones en ese grupo
     */
    Page<WebhookConfig> findByGroup(String group, Pageable pageable);

    /**
     * Busca configuraciones con un tag específico
     *
     * @param tag Tag a buscar
     * @param pageable Configuración de paginación
     * @return Página de configuraciones con ese tag
     */
    @Query("SELECT wc FROM WebhookConfig wc WHERE wc.tags LIKE %:tag%")
    Page<WebhookConfig> findByTagsContaining(@Param("tag") String tag, Pageable pageable);

    /**
     * Activa una configuración de webhook
     *
     * @param webhookId ID de la configuración
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookConfig wc SET wc.active = true, wc.updatedAt = CURRENT_TIMESTAMP WHERE wc.id = :webhookId")
    int activateWebhook(@Param("webhookId") UUID webhookId);

    /**
     * Desactiva una configuración de webhook
     *
     * @param webhookId ID de la configuración
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookConfig wc SET wc.active = false, wc.updatedAt = CURRENT_TIMESTAMP WHERE wc.id = :webhookId")
    int deactivateWebhook(@Param("webhookId") UUID webhookId);

    /**
     * Actualiza la URL de destino de un webhook
     *
     * @param webhookId ID de la configuración
     * @param newUrl Nueva URL de destino
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookConfig wc SET wc.targetUrl = :newUrl, wc.updatedAt = CURRENT_TIMESTAMP WHERE wc.id = :webhookId")
    int updateTargetUrl(@Param("webhookId") UUID webhookId, @Param("newUrl") String newUrl);

    /**
     * Actualiza el secreto de un webhook
     *
     * @param webhookId ID de la configuración
     * @param newSecret Nuevo secreto
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookConfig wc SET wc.secret = :newSecret, wc.updatedAt = CURRENT_TIMESTAMP WHERE wc.id = :webhookId")
    int updateSecret(@Param("webhookId") UUID webhookId, @Param("newSecret") String newSecret);

    /**
     * Actualiza todas las configuraciones de reintentos en un solo paso
     *
     * @param webhookId ID de la configuración
     * @param maxRetries Máximo de reintentos
     * @param backoffStrategy Estrategia de backoff
     * @param initialInterval Intervalo inicial
     * @param backoffFactor Factor de backoff
     * @param maxInterval Intervalo máximo
     * @return Número de registros actualizados
     */
    @Modifying
    @Transactional
    @Query("UPDATE WebhookConfig wc SET " +
            "wc.maxRetries = :maxRetries, " +
            "wc.backoffStrategy = :backoffStrategy, " +
            "wc.initialInterval = :initialInterval, " +
            "wc.backoffFactor = :backoffFactor, " +
            "wc.maxInterval = :maxInterval, " +
            "wc.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE wc.id = :webhookId")
    int updateRetryConfiguration(
            @Param("webhookId") UUID webhookId,
            @Param("maxRetries") int maxRetries,
            @Param("backoffStrategy") String backoffStrategy,
            @Param("initialInterval") int initialInterval,
            @Param("backoffFactor") double backoffFactor,
            @Param("maxInterval") int maxInterval);

    /**
     * Encuentra webhooks que no han sido actualizados recientemente
     *
     * @param cutoffDate Fecha límite para considerar antiguo
     * @param pageable Configuración de paginación
     * @return Página de configuraciones antiguas
     */
    Page<WebhookConfig> findByUpdatedAtBefore(OffsetDateTime cutoffDate, Pageable pageable);

    /**
     * Encuentra webhooks ordenados por número de mensajes asociados
     *
     * @param pageable Configuración de paginación
     * @return Página de configuraciones ordenadas por uso
     */
    @Query("SELECT wc, COUNT(m) as messageCount FROM WebhookConfig wc LEFT JOIN wc.messages m " +
            "GROUP BY wc.id ORDER BY messageCount DESC")
    Page<Object[]> findWebhooksByUsage(Pageable pageable);

    /**
     * Encuentra webhooks con tiempos de respuesta lentos
     *
     * @param threshold Umbral de tiempo en ms para considerar lento
     * @param pageable Configuración de paginación
     * @return Página de configuraciones con tiempos lentos
     */
    @Query("SELECT wc FROM WebhookConfig wc JOIN wc.healthStats hs " +
            "WHERE hs.averageResponseTime > :threshold " +
            "ORDER BY hs.averageResponseTime DESC")
    Page<WebhookConfig> findWebhooksWithSlowResponseTime(
            @Param("threshold") double threshold,
            Pageable pageable);

    /**
     * Encuentra webhooks con baja tasa de éxito
     *
     * @param threshold Umbral de tasa de éxito mínimo
     * @param minAttempts Cantidad mínima de intentos para considerar
     * @param pageable Configuración de paginación
     * @return Página de configuraciones problemáticas
     */
    @Query("SELECT wc FROM WebhookConfig wc JOIN wc.healthStats hs " +
            "WHERE hs.successRate < :threshold AND hs.totalAttempts >= :minAttempts " +
            "ORDER BY hs.successRate ASC")
    Page<WebhookConfig> findWebhooksWithLowSuccessRate(
            @Param("threshold") double threshold,
            @Param("minAttempts") long minAttempts,
            Pageable pageable);

    /**
     * Busca webhooks por múltiples criterios opcionales
     *
     * @param namePattern Patrón en el nombre (opcional)
     * @param urlPattern Patrón en la URL (opcional)
     * @param group Grupo (opcional)
     * @param active Estado de activación (opcional)
     * @param pageable Configuración de paginación
     * @return Página de configuraciones coincidentes
     */
    @Query("SELECT wc FROM WebhookConfig wc " +
            "WHERE (:namePattern IS NULL OR wc.name LIKE %:namePattern%) " +
            "AND (:urlPattern IS NULL OR wc.targetUrl LIKE %:urlPattern%) " +
            "AND (:group IS NULL OR wc.group = :group) " +
            "AND (:active IS NULL OR wc.active = :active)")
    Page<WebhookConfig> searchWebhooks(
            @Param("namePattern") String namePattern,
            @Param("urlPattern") String urlPattern,
            @Param("group") String group,
            @Param("active") Boolean active,
            Pageable pageable);

    /**
     * Agrupa webhooks por dominio de destino
     *
     * @return Lista de [dominio, conteo]
     */
    @Query(value =
            "SELECT " +
                    "SUBSTRING(target_url FROM '(?:.*?//)([^/]+)') as domain, " +
                    "COUNT(*) as count " +
                    "FROM webhook_configs " +
                    "GROUP BY domain " +
                    "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> countByTargetDomain();

    /**
     * Encuentra webhooks que no tienen mensajes asociados (no utilizados)
     *
     * @param cutoffDate Fecha límite para considerar no utilizado
     * @param pageable Configuración de paginación
     * @return Página de configuraciones no utilizadas
     */
    @Query("SELECT wc FROM WebhookConfig wc " +
            "WHERE wc.id NOT IN (SELECT DISTINCT m.webhookConfig.id FROM Message m) " +
            "AND wc.createdAt < :cutoffDate")
    Page<WebhookConfig> findUnusedWebhooks(
            @Param("cutoffDate") OffsetDateTime cutoffDate,
            Pageable pageable);

    /**
     * Encuentra configuraciones de webhook para un secreto específico
     * (útil para auditoría de seguridad)
     *
     * @param secretHash Hash parcial del secreto para búsqueda segura
     * @param pageable Configuración de paginación
     * @return Página de configuraciones coincidentes
     */
    @Query("SELECT wc FROM WebhookConfig wc WHERE FUNCTION('HASH', wc.secret) LIKE :secretHash")
    Page<WebhookConfig> findBySecretHash(@Param("secretHash") String secretHash, Pageable pageable);
}