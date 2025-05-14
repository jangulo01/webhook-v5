package com.sg.webhookservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.dto.DeliveryAttemptDto;
import com.sg.webhookservice.dto.MessageDto;
import com.sg.webhookservice.exception.ResourceNotFoundException;
import com.sg.webhookservice.exception.WebhookProcessingException;
import com.sg.webhookservice.model.DeliveryAttempt;
import com.sg.webhookservice.model.Message;
import com.sg.webhookservice.model.Message.MessageStatus;
import com.sg.webhookservice.model.WebhookConfig;
import com.sg.webhookservice.repository.DeliveryAttemptRepository;
import com.sg.webhookservice.repository.MessageRepository;
import com.sg.webhookservice.repository.WebhookConfigRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Métodos adicionales para la clase MessageProcessingService
 *
 * Implementa la funcionalidad de:
 * - Envío directo de mensajes
 * - Reintento en lote de mensajes fallidos
 * - Estadísticas de mensajes
 * - Limpieza de mensajes antiguos
 * - Reenvío forzado de mensajes específicos
 */
public class MessageProcessingServiceExtension {

    /**
     * Envía mensajes pendientes directamente.
     *
     * @param status Estado de los mensajes a enviar ('pending', 'failed', 'all')
     * @param limit Límite de mensajes a enviar
     * @param customDestinationUrl URL de destino personalizada (opcional)
     * @param customSecret Secreto personalizado (opcional)
     * @return Resultados de la operación
     */
    @Transactional
    /**
     * Envía mensajes pendientes directamente - Implementación provisional
     */
    @Transactional
    public Map<String, Object> directSendMessages(
            String status, Integer limit, String customDestinationUrl, String customSecret) {

        log.info("Iniciando envío directo. Status: {}, Limit: {}, DestinationUrl: {}",
                status, limit, customDestinationUrl != null ? customDestinationUrl : "default");

        // Simplemente devolver un resultado básico para probar compilación
        Map<String, Object> results = new HashMap<>();
        results.put("status", "success");
        results.put("message", "Implementación provisional");

        return results;
    }

    /**
     * Reintenta mensajes fallidos en lote - Implementación provisional
     */
    @Transactional
    public Map<String, Object> bulkRetryFailedMessages(
            Integer hours, Integer limit, String customDestinationUrl) {

        log.info("Iniciando reintento en lote. Horas: {}, Limit: {}, DestinationUrl: {}",
                hours, limit, customDestinationUrl != null ? customDestinationUrl : "default");

        // Simplemente devolver un resultado básico para probar compilación
        Map<String, Object> results = new HashMap<>();
        results.put("status", "success");
        results.put("message", "Implementación provisional");

        return results;
    }

    /**
     * Obtiene estadísticas de mensajes - Implementación provisional
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMessageStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("status", "success");
        stats.put("message", "Implementación provisional");

        return stats;
    }

    /**
     * Limpia mensajes antiguos - Implementación provisional
     */
    @Transactional
    public int cleanupOldMessages(int days) {
        log.info("Limpiando mensajes anteriores a {} días", days);
        return 0; // Implementación provisional
    }

    /**
     * Fuerza el reenvío de un mensaje específico - Implementación provisional
     */
    @Transactional
    public MessageDto forceResendMessage(String messageId, String destinationUrl) {
        log.info("Forzando reenvío de mensaje {}", messageId);

        // Convertir string a UUID
        UUID msgId = UUID.fromString(messageId);

        // Obtener mensaje
        Message message = getMessage(msgId);

        // Devolver DTO del mensaje tal como está
        return convertToDto(message);
    }

    /**
     * Obtiene estadísticas de mensajes agrupados por estado.
     *
     * @return Mapa con estadísticas
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMessageStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Estadísticas por estado
        Map<String, Long> byStatus = new HashMap<>();
        for (MessageStatus status : MessageStatus.values()) {
            long count = messageRepository.countByStatus(status);
            byStatus.put(status.name().toLowerCase(), count);
        }
        stats.put("by_status", byStatus);

        // Total de mensajes
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        stats.put("total", total);

        // Mensajes pendientes por webhook
        Map<String, Long> pendingByWebhook = messageRepository.countPendingByWebhook();
        stats.put("pending_by_webhook", pendingByWebhook);

        // Mensajes fallidos por webhook
        Map<String, Long> failedByWebhook = messageRepository.countFailedByWebhook();
        stats.put("failed_by_webhook", failedByWebhook);

        // Mensajes listos para reintento
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long readyForRetry = messageRepository.countMessagesReadyForRetry(now);
        stats.put("ready_for_retry", readyForRetry);

        // Últimos procesados
        stats.put("last_processed", messageRepository.findLastProcessed(10).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList()));

        return stats;
    }

    /**
     * Limpia mensajes antiguos para mantenimiento.
     *
     * @param days Días de antigüedad para eliminar
     * @return Número de mensajes eliminados
     */
    @Transactional
    public int cleanupOldMessages(int days) {
        if (days <= 0) {
            days = 30; // Valor por defecto: 30 días
        }

        OffsetDateTime cutoffDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        log.info("Limpiando mensajes anteriores a {}", cutoffDate);

        // Primero eliminamos los intentos de entrega antiguos
        int attemptCount = deliveryAttemptRepository.deleteForOldMessages(cutoffDate);
        log.info("Eliminados {} intentos de entrega antiguos", attemptCount);

        // Luego eliminamos los mensajes
        int messageCount = messageRepository.deleteOldMessages(cutoffDate);
        log.info("Eliminados {} mensajes antiguos", messageCount);

        return messageCount;
    }

    /**
     * Fuerza el reenvío de un mensaje específico.
     *
     * @param messageId ID del mensaje a reenviar
     * @param destinationUrl URL de destino personalizada (opcional)
     * @return DTO con información del mensaje reprocessado
     */
    @Transactional
    public MessageDto forceResendMessage(String messageId, String destinationUrl) {
        log.info("Forzando reenvío de mensaje {}", messageId);

        // Convertir string a UUID
        UUID msgId;
        try {
            msgId = UUID.fromString(messageId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ID de mensaje inválido: " + messageId);
        }

        // Obtener mensaje
        Message message = getMessage(msgId);

        // Aplicar URL personalizada si se proporciona
        if (destinationUrl != null && !destinationUrl.isEmpty()) {
            message.setTargetUrl(destinationUrl);
        }

        // Marcar como pendiente y procesar
        messageRepository.resetMessageStatus(msgId);

        try {
            // Procesar mensaje
            processMessage(msgId);

            // Obtener mensaje actualizado
            Message updatedMessage = getMessage(msgId);
            return convertToDto(updatedMessage);

        } catch (Exception e) {
            log.error("Error forzando reenvío de mensaje {}: {}", messageId, e.getMessage());
            throw new WebhookProcessingException(
                    "Error forzando reenvío: " + e.getMessage(),
                    e,
                    WebhookProcessingException.ProcessingPhase.DELIVERY,
                    message.getWebhookConfig().getName(),
                    messageId
            );
        }
    }
}