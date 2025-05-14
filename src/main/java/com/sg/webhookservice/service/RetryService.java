package com.sg.webhookservice.service;

import com.sg.webhookservice.model.Message;
import com.sg.webhookservice.model.DeliveryAttempt;
import com.sg.webhookservice.model.WebhookConfig;
import com.sg.webhookservice.repository.MessageRepository;
import com.sg.webhookservice.repository.WebhookConfigRepository;
import com.sg.webhookservice.dto.RetryRequestDto;
import com.sg.webhookservice.exception.ResourceNotFoundException;
import com.sg.webhookservice.kafka.producer.KafkaProducerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio responsable de gestionar la lógica de reintentos para mensajes de webhook fallidos.
 * Implementa diferentes estrategias de backoff y programa reintentos basados en configuraciones.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RetryService {

    private final MessageRepository messageRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final KafkaProducerService kafkaProducerService;

    @Value("${app.direct-mode:false}")
    private boolean directMode;

    /**
     * Calcula el tiempo para el próximo reintento basado en la configuración del webhook
     * y el estado actual de reintentos.
     *
     * @param message El mensaje que ha fallado
     * @param attempt El intento de entrega fallido
     * @param config La configuración del webhook
     * @return Tiempo calculado para el próximo reintento
     */
    public OffsetDateTime calculateNextRetryTime(Message message, DeliveryAttempt attempt, WebhookConfig config) {
        int retryCount = message.getRetryCount();
        String backoffStrategy = config.getBackoffStrategy();
        int initialInterval = config.getInitialInterval();
        double backoffFactor = config.getBackoffFactor();
        int maxInterval = config.getMaxInterval();

        // Ajustar el intervalo basado en la respuesta del servidor
        // Por ejemplo, si tenemos un 429 (Too Many Requests), podríamos aumentar el intervalo
        double adjustmentFactor = 1.0;
        if (attempt != null && attempt.getStatusCode() != null) {
            if (attempt.getStatusCode() == 429) {
                // Aumentar significativamente el intervalo para rate limiting
                adjustmentFactor = 2.0;
            } else if (attempt.getStatusCode() >= 500) {
                // Aumentar moderadamente para errores de servidor
                adjustmentFactor = 1.5;
            }
        }

        // Calcular tiempo de espera según la estrategia configurada
        long delayInSeconds;

        switch (backoffStrategy.toLowerCase()) {
            case "linear":
                delayInSeconds = (long) (Math.min(initialInterval * (1 + retryCount), maxInterval) * adjustmentFactor);
                break;
            case "exponential":
                delayInSeconds = (long) (Math.min(initialInterval * Math.pow(backoffFactor, retryCount), maxInterval) * adjustmentFactor);
                break;
            case "fixed":
                delayInSeconds = (long) (initialInterval * adjustmentFactor);
                break;
            default:
                // Si no reconocemos la estrategia, usar exponencial por defecto
                delayInSeconds = (long) (Math.min(initialInterval * Math.pow(2.0, retryCount), maxInterval) * adjustmentFactor);
        }

        return OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delayInSeconds);
    }

    /**
     * Maneja errores durante el reintento.
     *
     * @param messageId ID del mensaje con error
     * @param exception La excepción ocurrida
     */
    @Transactional
    public void handleRetryError(UUID messageId, Exception exception) {
        log.error("Error durante el reintento del mensaje {}: {}", messageId, exception.getMessage());

        try {
            // Intentar recuperar el mensaje
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new ResourceNotFoundException("Mensaje no encontrado: " + messageId));

            // Actualizar el estado para reflejar el error
            message.setLastError("Error durante reintento: " + exception.getMessage());
            // Mantener nextRetry si existe, para posible futuro reintento

            messageRepository.save(message);
        } catch (Exception e) {
            // Si hay un error incluso actualizando el mensaje, solo loguearlo
            log.error("Error adicional al actualizar estado de error del mensaje {}: {}",
                    messageId, e.getMessage(), e);
        }
    }

    /**
     * Actualiza backoffs dinámicos basados en el comportamiento del destino.
     * Útil para destinos con problemas recurrentes o limitaciones de velocidad.
     *
     * @return Número de destinos actualizados
     */
    @Transactional
    public int updateDynamicBackoffs() {
        // Esta implementación es un placeholder. En un sistema real, podría:
        // 1. Analizar patrones de fallo para destinos específicos
        // 2. Ajustar dinámicamente los backoffs basados en esos patrones
        // 3. Persistir esos ajustes en una tabla específica

        log.info("Actualizando backoffs dinámicos basados en patrones de entrega...");

        // Ejemplo muy simplificado - en un sistema real podrías hacer queries
        // para identificar destinos problemáticos y aplicar ajustes específicos

        return 0; // Número de destinos actualizados
    }

    /**
     * Registra estadísticas de reintentos para análisis.
     */
    public void logRetryStatistics() {
        // Placeholder para implementación real de estadísticas
        log.info("Generando estadísticas de reintentos...");

        // En una implementación real, podría:
        // 1. Consultar el repository para obtener estadísticas de reintentos
        // 2. Registrarlas o exponerlas a través de métricas
        // 3. Identificar patrones o anomalías
    }

    /**
     * Procesa una petición de reintento en lote.
     *
     * @param request DTO con parámetros para el reintento en lote
     * @return Mapa con estadísticas del reintento
     */
    @Transactional
    public Map<String, Object> processBulkRetry(RetryRequestDto request) {
        int hours = request.getHours() != null ? request.getHours() : 24;
        int limit = request.getLimit() != null ? request.getLimit() : 100;
        String destinationUrl = request.getDestinationUrl();

        OffsetDateTime cutoffTime = OffsetDateTime.now().minusHours(hours);

        // Encontrar mensajes fallidos dentro del período especificado
        List<Message> failedMessages = messageRepository.findByStatusAndUpdatedAtAfter(
                Message.MessageStatus.FAILED,
                cutoffTime,
                PageRequest.of(0, limit)
        );

        Map<String, Object> results = new HashMap<>();
        results.put("total", failedMessages.size());
        results.put("scheduled", 0);
        results.put("failed", 0);

        List<Map<String, Object>> messageResults = failedMessages.stream()
                .map(message -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("message_id", message.getId().toString());
                    result.put("webhook_name", message.getWebhookConfig().getName());

                    try {
                        // Programar reintento
                        message.setStatus(Message.MessageStatus.PENDING);
                        if (destinationUrl != null && !destinationUrl.isEmpty()) {
                            message.setTargetUrl(destinationUrl);
                        }
                        message.setUpdatedAt(OffsetDateTime.now());
                        messageRepository.save(message);

                        // Enviar a Kafka para procesamiento si no estamos en modo directo
                        if (!directMode) {
                            kafkaProducerService.sendWebhookMessage(message.getId().toString());
                        }

                        results.put("scheduled", (Integer) results.get("scheduled") + 1);
                        result.put("status", "scheduled");
                    } catch (Exception e) {
                        log.error("Error programando reintento para mensaje {}: {}",
                                message.getId(), e.getMessage(), e);
                        results.put("failed", (Integer) results.get("failed") + 1);
                        result.put("status", "error");
                        result.put("error", e.getMessage());
                    }

                    return result;
                })
                .collect(Collectors.toList());

        results.put("messages", messageResults);
        return results;
    }
}