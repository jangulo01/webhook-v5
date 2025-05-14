package com.sg.webhookservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.sg.webhookservice.kafka.producer.WebhookMessageProducer;
import com.sg.webhookservice.dto.WebhookRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Servicio principal para el procesamiento de mensajes de webhook.
 * Gestiona la recepción, validación, almacenamiento y entrega asincrónica
 * de mensajes webhook a sus destinos.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageProcessingService {

    private final MessageRepository messageRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final ObjectMapper objectMapper;
    private final WebhookMessageProducer webhookMessageProducer;
    private final HmacService hmacService;
    private final MessageSenderService messageSenderService;
    private final HealthMonitoringService healthMonitoringService;

    @Value("${app.processing.direct-mode:false}")
    private boolean directMode;

    @Value("${app.processing.kafka-topic:webhook-events}")
    private String kafkaTopic;

    @Value("${app.processing.kafka-retry-topic:webhook-retries}")
    private String kafkaRetryTopic;

    @Value("${app.processing.max-payload-log-length:1000}")
    private int maxPayloadLogLength;

    @Value("${app.processing.destination-url-override:#{null}}")
    private String destinationUrlOverride;

    // Los métodos serán implementados en las siguientes partes
    /**
     * Recibe un mensaje de webhook, lo valida y lo encola para procesamiento.
     *
     * @param webhookName Nombre del webhook configurado
     * @param requestDto Objeto con los datos del webhook
     * @return ID del mensaje creado
     * @throws ResourceNotFoundException Si la configuración del webhook no existe
     * @throws WebhookProcessingException Si hay un error en el procesamiento
     */
    @Transactional
    public UUID receiveWebhook(String webhookName, WebhookRequestDto requestDto) {
        log.info("Recibiendo webhook para configuración: {}", webhookName);

        try {
            // Obtener configuración del webhook
            WebhookConfig webhookConfig = webhookConfigRepository.findByNameAndActiveTrue(webhookName)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Configuración de webhook no encontrada o inactiva: " + webhookName,
                            "webhookConfig",
                            webhookName
                    ));

            // Validar payload
            String payload = validateAndNormalizePayload(requestDto.getPayload());

            // Crear y guardar el mensaje
            Message message = createMessage(webhookConfig, payload);

            // Encolar para procesamiento
            queueMessageForProcessing(message);

            // Actualizar estadísticas
            healthMonitoringService.recordReceivedMessage(webhookConfig.getId());

            return message.getId();
        } catch (ResourceNotFoundException e) {
            // Re-lanzar excepciones de recursos no encontrados
            throw e;
        } catch (Exception e) {
            log.error("Error procesando webhook {}: {}", webhookName, e.getMessage(), e);
            throw new WebhookProcessingException(
                    "Error procesando webhook",
                    e,
                    WebhookProcessingException.ProcessingPhase.RECEPTION,
                    webhookName,
                    null
            );
        }
    }

    /**
     * Valida y normaliza el payload recibido.
     *
     * @param payload Payload recibido
     * @return Payload normalizado como string JSON
     * @throws WebhookProcessingException Si el payload es inválido
     */
    private String validateAndNormalizePayload(Object payload) {
        if (payload == null) {
            throw new WebhookProcessingException(
                    "Payload vacío o nulo",
                    null,
                    WebhookProcessingException.ProcessingPhase.VALIDATION,
                    null,
                    null
            );
        }

        try {
            // Si ya es un String, verificar que sea JSON válido
            if (payload instanceof String) {
                String payloadStr = (String) payload;
                // Intentar parsear para validar
                objectMapper.readTree(payloadStr);
                return payloadStr;
            }
            // Si es un objeto, convertirlo a JSON
            else {
                return objectMapper.writeValueAsString(payload);
            }
        } catch (Exception e) {
            log.error("Error validando payload: {}", e.getMessage(), e);
            throw new WebhookProcessingException(
                    "Payload inválido: " + e.getMessage(),
                    e,
                    WebhookProcessingException.ProcessingPhase.VALIDATION,
                    null,
                    null
            );
        }
    }

    /**
     * Crea y guarda un nuevo mensaje a partir de la configuración y el payload.
     *
     * @param webhookConfig Configuración del webhook
     * @param payload Payload normalizado
     * @return Mensaje creado y guardado
     */
    private Message createMessage(WebhookConfig webhookConfig, String payload) {
        // Generar firma HMAC
        String signature = hmacService.generateSignature(payload, webhookConfig.getSecret(), true);

        // Determinar URL de destino (con posible override global)
        String targetUrl = destinationUrlOverride != null && !destinationUrlOverride.isEmpty()
                ? destinationUrlOverride
                : webhookConfig.getTargetUrl();

        // Crear mensaje
        Message message = new Message();
        message.setWebhookConfig(webhookConfig);
        message.setPayload(payload);
        message.setTargetUrl(targetUrl);
        message.setStatus(MessageStatus.PENDING);
        message.setSignature(signature);
        message.setHeaders(webhookConfig.getHeaders());
        message.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        message.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        message.setRetryCount(0);

        // Guardar mensaje
        return messageRepository.save(message);
    }

    /**
     * Encola un mensaje para procesamiento según el modo configurado.
     *
     * @param message Mensaje a encolar
     */
    private void queueMessageForProcessing(Message message) {
        UUID messageId = message.getId();
        String webhookName = message.getWebhookConfig().getName();

        // Modo directo (sin Kafka)
        if (directMode) {
            log.info("Modo directo: procesando mensaje {} inmediatamente", messageId);
            // Enviar mensaje para procesamiento asíncrono
            CompletableFuture.runAsync(() -> {
                try {
                    messageSenderService.processMessage(messageId);
                } catch (Exception e) {
                    log.error("Error en procesamiento directo del mensaje {}: {}",
                            messageId, e.getMessage(), e);
                }
            });
        }
        // Modo Kafka
        else {
            log.info("Enviando mensaje {} a Kafka (topic: {})", messageId, kafkaTopic);
            // Publicar ID del mensaje en Kafka
            webhookMessageProducer.sendMessage(kafkaTopic, messageId.toString());
        }

        log.info("Mensaje {} del webhook {} encolado para procesamiento",
                messageId, webhookName);
    }

    /**
     * Comprueba y procesa mensajes pendientes en la base de datos.
     * Este método se utiliza principalmente al iniciar la aplicación
     * para procesar mensajes que pudieron quedar pendientes durante
     * el apagado anterior.
     *
     * @param limit Límite de mensajes a procesar
     * @return Número de mensajes encontrados
     */
    @Transactional(readOnly = true)
    public int checkPendingMessages(int limit) {
        log.info("Comprobando mensajes pendientes en base de datos (límite: {})", limit);

        // Obtener mensajes pendientes
        var pendingMessages = messageRepository.findByStatusOrderByCreatedAtAsc(
                MessageStatus.PENDING, limit);

        log.info("Encontrados {} mensajes pendientes", pendingMessages.size());

        // Procesar cada mensaje
        for (Message message : pendingMessages) {
            try {
                if (directMode) {
                    // Enviar directamente
                    CompletableFuture.runAsync(() ->
                            messageSenderService.processMessage(message.getId()));
                    log.info("Mensaje pendiente {} enviado para procesamiento directo", message.getId());
                } else {
                    // Enviar a Kafka
                    webhookMessageProducer.sendMessage(kafkaTopic, message.getId().toString());
                    log.info("Mensaje pendiente {} enviado a Kafka", message.getId());
                }
            } catch (Exception e) {
                log.error("Error encolando mensaje pendiente {}: {}",
                        message.getId(), e.getMessage(), e);
            }
        }

        // Comprobar mensajes con reintentos pendientes
        var retryMessages = checkPendingRetries(limit);

        return pendingMessages.size() + retryMessages;
    }

    /**
     * Comprueba y programa reintentos pendientes.
     *
     * @param limit Límite de mensajes a procesar
     * @return Número de mensajes encontrados para reintento
     */
    @Transactional(readOnly = true)
    public int checkPendingRetries(int limit) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        log.info("Comprobando mensajes con reintentos pendientes (límite: {})", limit);

        // Obtener mensajes fallidos con reintento programado
        var retryMessages = messageRepository.findMessagesReadyForRetry(now, limit);

        log.info("Encontrados {} mensajes listos para reintento", retryMessages.size());

        // Procesar cada mensaje para reintento
        for (Message message : retryMessages) {
            try {
                if (directMode) {
                    // Enviar directamente
                    CompletableFuture.runAsync(() ->
                            messageSenderService.processMessage(message.getId()));
                    log.info("Mensaje de reintento {} enviado para procesamiento directo",
                            message.getId());
                } else {
                    // Enviar a Kafka (topic de reintentos)
                    webhookMessageProducer.sendMessage(kafkaRetryTopic, message.getId().toString());
                    log.info("Mensaje de reintento {} enviado a Kafka", message.getId());
                }
            } catch (Exception e) {
                log.error("Error encolando mensaje de reintento {}: {}",
                        message.getId(), e.getMessage(), e);
            }
        }

        return retryMessages.size();
    }

    /**
     * Cancela un mensaje, evitando su procesamiento o reintentos futuros.
     *
     * @param messageId ID del mensaje a cancelar
     * @return true si el mensaje fue cancelado, false si no existía o ya estaba entregado/cancelado
     */
    @Transactional
    public boolean cancelMessage(UUID messageId) {
        log.info("Cancelando mensaje: {}", messageId);

        return messageRepository.findById(messageId)
                .map(message -> {
                    // Solo cancelar si está pendiente o falló
                    if (message.getStatus() == MessageStatus.PENDING ||
                            message.getStatus() == MessageStatus.FAILED ||
                            message.getStatus() == MessageStatus.PROCESSING) {

                        message.setStatus(MessageStatus.CANCELLED);
                        message.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        message.setLastError("Cancelado manualmente");
                        message.setNextRetry(null);
                        messageRepository.save(message);

                        log.info("Mensaje {} cancelado correctamente", messageId);
                        return true;
                    } else {
                        log.warn("No se puede cancelar mensaje {} en estado {}",
                                messageId, message.getStatus());
                        return false;
                    }
                })
                .orElseGet(() -> {
                    log.warn("Intento de cancelar mensaje inexistente: {}", messageId);
                    return false;
                });
    }

    /**
     * Reprograma un mensaje fallido para un reintento inmediato.
     *
     * @param messageId ID del mensaje a reprogramar
     * @return true si el mensaje fue reprogramado, false si no existía o no estaba en estado fallido
     */
    @Transactional
    public boolean rescheduleMessage(UUID messageId) {
        log.info("Reprogramando mensaje para reintento inmediato: {}", messageId);

        return messageRepository.findById(messageId)
                .map(message -> {
                    // Solo reprogramar si está en estado fallido
                    if (message.getStatus() == MessageStatus.FAILED) {
                        message.setStatus(MessageStatus.PENDING);
                        message.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                        message.setNextRetry(null);
                        messageRepository.save(message);

                        // Encolar inmediatamente
                        queueMessageForProcessing(message);

                        log.info("Mensaje {} reprogramado correctamente", messageId);
                        return true;
                    } else {
                        log.warn("No se puede reprogramar mensaje {} en estado {}",
                                messageId, message.getStatus());
                        return false;
                    }
                })
                .orElseGet(() -> {
                    log.warn("Intento de reprogramar mensaje inexistente: {}", messageId);
                    return false;
                });
    }

    /**
     * Envía mensajes pendientes directamente, sin usar Kafka.
     * Útil para operaciones administrativas o recuperación de errores.
     *
     * @param status Estado de los mensajes a enviar ("pending", "failed", "all")
     * @param limit Límite de mensajes a procesar
     * @param customDestinationUrl URL de destino personalizada (opcional)
     * @param customSecret Secreto personalizado para firma (opcional)
     * @return Mapa con estadísticas del proceso
     */
    @Transactional(readOnly = true)
    public Map<String, Object> directSendMessages(String status, int limit,
                                                  String customDestinationUrl, String customSecret) {

        log.info("Enviando directamente mensajes con estado {}, límite: {}", status, limit);

        List<Message> messages;

        // Obtener mensajes según su estado
        if ("all".equalsIgnoreCase(status)) {
            messages = messageRepository.findByStatusInOrderByCreatedAtAsc(
                    Arrays.asList(MessageStatus.PENDING, MessageStatus.FAILED), limit);
        } else if ("pending".equalsIgnoreCase(status)) {
            messages = messageRepository.findByStatusOrderByCreatedAtAsc(MessageStatus.PENDING, limit);
        } else if ("failed".equalsIgnoreCase(status)) {
            messages = messageRepository.findByStatusOrderByCreatedAtAsc(MessageStatus.FAILED, limit);
        } else {
            throw new IllegalArgumentException("Estado no válido: " + status);
        }

        log.info("Encontrados {} mensajes para envío directo", messages.size());

        // Estadísticas
        Map<String, Object> results = new HashMap<>();
        results.put("total", messages.size());
        results.put("successful", 0);
        results.put("failed", 0);

        List<Map<String, Object>> messageResults = new ArrayList<>();

        // Procesar cada mensaje
        for (Message message : messages) {
            try {
                // Enviar mensaje directamente
                boolean success = messageSenderService.sendMessageWithCustomDestination(
                        message.getId(), customDestinationUrl, customSecret);

                // Registrar resultado
                Map<String, Object> messageResult = new HashMap<>();
                messageResult.put("message_id", message.getId().toString());
                messageResult.put("webhook_name", message.getWebhookConfig().getName());
                messageResult.put("status", success ? "delivered" : "failed");

                messageResults.add(messageResult);

                // Actualizar contador
                if (success) {
                    results.put("successful", ((Integer) results.get("successful")) + 1);
                } else {
                    results.put("failed", ((Integer) results.get("failed")) + 1);
                }

                // Pequeña pausa entre envíos
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

            } catch (Exception e) {
                log.error("Error en envío directo del mensaje {}: {}",
                        message.getId(), e.getMessage(), e);

                // Registrar error
                Map<String, Object> messageResult = new HashMap<>();
                messageResult.put("message_id", message.getId().toString());
                messageResult.put("webhook_name", message.getWebhookConfig().getName());
                messageResult.put("status", "error");
                messageResult.put("error", e.getMessage());

                messageResults.add(messageResult);
                results.put("failed", ((Integer) results.get("failed")) + 1);
            }
        }

        results.put("messages", messageResults);
        return results;
    }

    /**
     * Realiza reintentos masivos de mensajes fallidos en un período de tiempo.
     *
     * @param hours                Horas hacia atrás para considerar mensajes fallidos
     * @param limit                Límite de mensajes a procesar
     * @param customDestinationUrl URL de destino personalizada (opcional)
     * @return Mapa con estadísticas del proceso
     */
    @Transactional(readOnly = true)
    public Map<String, Object> bulkRetryFailedMessages(int hours, int limit, String customDestinationUrl) {
        return null;
    }

    /**
     * Limpia intentos de entrega antiguos para optimizar la base de datos.
     *
     * @param olderThanDays Días de antigüedad para considerar intentos obsoletos
     * @return Número de registros eliminados
     */
    @Transactional
    public int cleanupOldDeliveryAttempts(int olderThanDays) {
        OffsetDateTime cutoffDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(olderThanDays);

        log.info("Limpiando intentos de entrega anteriores a: {}", cutoffDate);

        int deletedCount = deliveryAttemptRepository.deleteByTimestampBefore(cutoffDate);

        log.info("Eliminados {} intentos de entrega antiguos", deletedCount);

        return deletedCount;
    }

    /**
     * Método auxiliar para truncar strings largos en logs.
     *
     * @param text Texto a truncar
     * @return Texto truncado si excede la longitud máxima
     */
    private String truncateForLog(String text) {
        if (text == null) {
            return "null";
        }

        if (text.length() <= maxPayloadLogLength) {
            return text;
        }

        return text.substring(0, maxPayloadLogLength) + "... [truncado]";
    }


    public void processRetry(UUID messageId) {
    }

    public MessageDto getMessageById(UUID id) {
        return null;
    }
}
