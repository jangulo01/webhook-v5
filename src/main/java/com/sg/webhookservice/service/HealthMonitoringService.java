package com.sg.webhookservice.service;

import com.sg.webhookservice.presentation.dto.HealthResponseDto;
import com.sg.webhookservice.presentation.dto.WebhookHealthStatsDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interfaz para el servicio de monitoreo de salud.
 * Proporciona funcionalidad para supervisar el estado del sistema y los webhooks.
 */
public interface HealthMonitoringService {

    /**
     * Obtiene el estado general de salud del servicio.
     *
     * @return DTO con información de salud
     */
    HealthResponseDto getServiceHealth();

    /**
     * Obtiene estadísticas de salud para un webhook específico.
     *
     * @param webhookId ID del webhook
     * @return Estadísticas de salud
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    WebhookHealthStatsDto getWebhookHealthStats(UUID webhookId);

    /**
     * Obtiene estadísticas de salud para un webhook por nombre.
     *
     * @param webhookName Nombre del webhook
     * @return Estadísticas de salud
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    WebhookHealthStatsDto getWebhookHealthStatsByName(String webhookName);

    /**
     * Obtiene estadísticas de salud para todos los webhooks.
     *
     * @return Lista de estadísticas
     */
    List<WebhookHealthStatsDto> getAllWebhookHealthStats();

    /**
     * Obtiene webhooks con problemas de salud.
     *
     * @param minSuccess Tasa mínima de éxito para considerar saludable
     * @return Lista de webhooks con problemas
     */
    List<WebhookHealthStatsDto> getUnhealthyWebhooks(double minSuccess);

    /**
     * Registra una entrega exitosa.
     *
     * @param webhookId ID del webhook
     * @param statusCode Código de estado HTTP
     * @param responseTimeMs Tiempo de respuesta en milisegundos
     */
    void recordSuccessfulDelivery(UUID webhookId, int statusCode, long responseTimeMs);

    /**
     * Registra una entrega fallida.
     *
     * @param webhookId ID del webhook
     */
    void recordFailedDelivery(UUID webhookId);

    /**
     * Registra una entrega fallida con mensaje de error.
     *
     * @param webhookId ID del webhook
     * @param errorMessage Mensaje de error
     */
    void recordFailedDelivery(UUID webhookId, String errorMessage);

    /**
     * Registra un error de conexión.
     *
     * @param webhookId ID del webhook
     */
    void recordConnectionError(UUID webhookId);

    /**
     * Obtiene estadísticas globales del servicio.
     *
     * @return Mapa con estadísticas globales
     */
    Map<String, Object> getGlobalStatistics();

    /**
     * Realiza un chequeo completo de salud para todos los webhooks y actualiza
     * su estado.
     *
     * @return Número de webhooks analizados
     */
    int performHealthCheck();
}