package com.sg.webhookservice.domain.repository;

import com.sg.webhookservice.domain.entity.WebhookHealthStats;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interfaz repositorio para la entidad WebhookHealthStats.
 * Define los métodos para gestionar estadísticas de salud de webhooks.
 */
public interface WebhookHealthStatsRepository {

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
    List<WebhookHealthStats> findAllOrderBySuccessRate();

    /**
     * Registra una entrega exitosa.
     *
     * @param webhookConfigId ID de la configuración de webhook
     * @param responseTimeMs Tiempo de respuesta en milisegundos
     * @return Número de filas afectadas
     */
    int recordSuccessfulDelivery(UUID webhookConfigId, long responseTimeMs);

    /**
     * Registra una entrega fallida.
     *
     * @param webhookConfigId ID de la configuración de webhook
     * @param errorMessage Mensaje de error
     * @return Número de filas afectadas
     */
    int recordFailedDelivery(UUID webhookConfigId, String errorMessage);

    /**
     * Actualiza el nombre del webhook en las estadísticas cuando cambia en la configuración.
     *
     * @param webhookConfigId ID de la configuración de webhook
     * @param newName Nuevo nombre
     * @return Número de filas afectadas
     */
    int updateWebhookName(UUID webhookConfigId, String newName);

    /**
     * Encuentra webhooks con problemas de salud (baja tasa de éxito).
     *
     * @param minimumSent Mínimo de mensajes enviados para considerar
     * @param successRateThreshold Umbral de tasa de éxito
     * @return Lista de estadísticas de webhooks con problemas
     */
    List<WebhookHealthStats> findUnhealthyWebhooks(int minimumSent, double successRateThreshold);

    /**
     * Obtiene estadísticas resumidas para todos los webhooks.
     *
     * @return Objeto con totales globales
     */
    Object[] getGlobalStats();

    /**
     * Encuentra webhooks inactivos (sin actividad reciente).
     *
     * @param cutoffTime Tiempo límite de inactividad
     * @return Lista de estadísticas de webhooks inactivos
     */
    List<WebhookHealthStats> findInactiveWebhooks(OffsetDateTime cutoffTime);

    /**
     * Guarda una entidad WebhookHealthStats.
     *
     * @param entity La entidad a guardar
     * @return La entidad guardada
     */
    WebhookHealthStats save(WebhookHealthStats entity);

    /**
     * Busca una entidad por su ID.
     *
     * @param id ID de la entidad
     * @return Entidad encontrada o Optional vacío
     */
    Optional<WebhookHealthStats> findById(UUID id);

    /**
     * Obtiene todas las entidades.
     *
     * @return Lista de entidades
     */
    List<WebhookHealthStats> findAll();

    /**
     * Elimina una entidad por su ID.
     *
     * @param id ID de la entidad a eliminar
     */
    void deleteById(UUID id);

    /**
     * Verifica si existe una entidad con el ID dado.
     *
     * @param id ID a verificar
     * @return true si existe, false si no
     */
    boolean existsById(UUID id);
}