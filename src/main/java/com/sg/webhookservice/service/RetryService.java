package com.sg.webhookservice.service;

import com.sg.webhookservice.domain.entity.Message;
import com.sg.webhookservice.domain.entity.WebhookConfig;
import com.sg.webhookservice.presentation.dto.BulkRetryRequestDto;
import com.sg.webhookservice.presentation.dto.BulkRetryResponseDto;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Interfaz para el servicio de gestión de reintentos de mensajes fallidos.
 * Maneja las estrategias de backoff y la programación de reintentos.
 */
public interface RetryService {

    /**
     * Calcula el momento para el próximo reintento según la configuración del webhook.
     *
     * @param config Configuración del webhook
     * @param retryCount Número actual de intentos
     * @return Fecha y hora para el próximo reintento
     */
    OffsetDateTime calculateNextRetry(WebhookConfig config, int retryCount);

    /**
     * Calcula el momento para el próximo reintento basado en un mensaje existente.
     *
     * @param message Mensaje fallido
     * @return Fecha y hora para el próximo reintento
     */
    OffsetDateTime calculateNextRetryForMessage(Message message);

    /**
     * Programa reintentos para mensajes fallidos.
     * Este método es invocado periódicamente por un scheduler.
     *
     * @return Número de mensajes procesados
     */
    int processScheduledRetries();

    /**
     * Procesa una solicitud de reintento masivo.
     *
     * @param request Parámetros para la operación
     * @return Resultado de la operación
     */
    BulkRetryResponseDto processBulkRetry(BulkRetryRequestDto request);

    /**
     * Verifica si un mensaje debe reintentarse basado en su estado.
     *
     * @param message Mensaje a evaluar
     * @return true si el mensaje debe reintentarse
     */
    boolean shouldRetryMessage(Message message);

    /**
     * Actualiza los datos de reintento de un mensaje.
     *
     * @param messageId ID del mensaje
     * @return Mensaje actualizado
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    Message updateMessageForRetry(UUID messageId);

    /**
     * Obtiene el número de mensajes en espera de reintento.
     *
     * @return Conteo de mensajes
     */
    long getMessagesAwaitingRetryCount();

    /**
     * Obtiene mensajes expirados.
     *
     * @param maxAgeHours Edad máxima en horas
     * @param limit Límite de resultados
     * @return Lista de mensajes expirados
     */
    List<Message> getExpiredMessages(int maxAgeHours, int limit);

    /**
     * Calcula el tiempo total estimado para reintentos.
     *
     * @param config Configuración del webhook
     * @param maxRetries Número máximo de reintentos
     * @return Duración total estimada
     */
    Duration calculateEstimatedRetryPeriod(WebhookConfig config, int maxRetries);
}
