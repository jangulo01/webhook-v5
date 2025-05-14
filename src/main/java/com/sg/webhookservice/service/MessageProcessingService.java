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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Servicio central para procesamiento de mensajes de webhook.
 * 
 * Maneja todo el ciclo de vida del procesamiento de mensajes, incluyendo:
 * - Procesamiento inicial de mensajes
 * - Entrega a destinos
 * - Manejo de reintentos
 * - Gestión de errores y excepciones
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageProcessingService {

    private final MessageRepository messageRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final RetryService retryService;
    private final HealthMonitoringService healthMonitoringService;
    
    @Value("${app.processing.connection-timeout-ms:5000}")
    private int connectionTimeoutMs;
    
    @Value("${app.processing.read-timeout-ms:10000}")
    private int readTimeoutMs;
    
    @Value("${app.processing.max-payload-log-length:1000}")
    private int maxPayloadLogLength;
    
    @Value("${app.processing.node-identifier:#{null}}")
    private String nodeIdentifier;
    
    /**
     * Procesa un mensaje recién recibido o previamente pendiente.
     * 
     * @param messageId ID del mensaje a procesar
     * @throws WebhookProcessingException Si ocurre error en el procesamiento
     * @throws ResourceNotFoundException Si el mensaje no existe
     */
    @Transactional
    public void processMessage(@NotNull UUID messageId) {
        log.info("Procesando mensaje: {}", messageId);
        
        // Obtener mensaje con bloqueo para actualización
        Message message = getMessage(messageId);
        
        try {
            // Intentar marcar como en procesamiento
            int updated = messageRepository.markAsProcessing(messageId);
            if (updated == 0) {
                log.warn("No se pudo marcar mensaje {} como en procesamiento, posible estado incorrecto", messageId);
                // Verificar estado actual para diagnóstico
                log.info("Estado actual del mensaje {}: {}", messageId, message.getStatus());
                return;
            }
            
            // Verificar que el webhook esté activo
            WebhookConfig config = message.getWebhookConfig();
            if (!config.isActive()) {
                log.warn("Webhook {} está inactivo, cancelando mensaje {}", 
                        config.getName(), messageId);
                messageRepository.cancelMessage(messageId);
                return;
            }
            
            // Enviar mensaje a destino
            deliverMessage(message);
            
        } catch (WebhookProcessingException e) {
            // Propagar excepciones de webhook sin envolver
            throw e;
        } catch (Exception e) {
            log.error("Error procesando mensaje {}: {}", messageId, e.getMessage(), e);
            throw new WebhookProcessingException(
                    "Error en procesamiento de mensaje",
                    e,
                    WebhookProcessingException.ProcessingPhase.PREPARATION,
                    message.getWebhookConfig().getName(),
                    messageId.toString()
            );
        }
    }

    /**
     * Procesa un reintento de mensaje fallido.
     * 
     * @param messageId ID del mensaje a reintentar
     * @throws WebhookProcessingException Si ocurre error en el procesamiento
     * @throws ResourceNotFoundException Si el mensaje no existe
     */
    @Transactional
    public void processRetry(@NotNull UUID messageId) {
        log.info("Procesando reintento para mensaje: {}", messageId);
        
        // Obtener mensaje
        Message message = getMessage(messageId);
        
        // Verificar que esté en estado adecuado para reintento
        if (message.getStatus() != MessageStatus.FAILED || message.getNextRetry() == null) {
            log.warn("Mensaje {} no está en estado adecuado para reintento. Estado: {}, NextRetry: {}", 
                    messageId, message.getStatus(), message.getNextRetry());
            return;
        }
        
        // Verificar que sea tiempo de reintentar
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (message.getNextRetry().isAfter(now)) {
            log.info("Aún no es tiempo de reintentar mensaje {}. Programado para: {}", 
                    messageId, message.getNextRetry());
            return;
        }
        
        try {
            // Marcar como en procesamiento
            int updated = messageRepository.markAsProcessing(messageId);
            if (updated == 0) {
                log.warn("No se pudo marcar mensaje {} para reintento, posible concurrencia", messageId);
                return;
            }
            
            // Verificar si ya excedió el máximo de reintentos
            if (message.hasExceededMaxRetries()) {
                log.warn("Mensaje {} ha excedido el máximo de reintentos ({}), marcando como fallido definitivo", 
                        messageId, message.getRetryCount());
                
                // Marcar como fallido sin más reintentos
                messageRepository.markAsFailed(
                        messageId,
                        "Máximo de reintentos excedido",
                        null
                );
                
                // Actualizar estadísticas de salud
                healthMonitoringService.recordFailedDelivery(message.getWebhookConfig().getId());
                
                return;
            }
            
            // Verificar que el webhook siga activo
            WebhookConfig config = message.getWebhookConfig();
            if (!config.isActive()) {
                log.warn("Webhook {} está inactivo, cancelando reintento {}", 
                        config.getName(), messageId);
                messageRepository.cancelMessage(messageId);
                return;
            }
            
            // Incrementar contador de reintentos
            messageRepository.incrementRetryCount(messageId);
            
            // Entregar mensaje
            deliverMessage(message);
            
        } catch (WebhookProcessingException e) {
            // Propagar excepciones de webhook
            throw e;
        } catch (Exception e) {
            log.error("Error procesando reintento {}: {}", messageId, e.getMessage(), e);
            throw new WebhookProcessingException(
                    "Error en procesamiento de reintento",
                    e,
                    WebhookProcessingException.ProcessingPhase.RETRY_SCHEDULING,
                    message.getWebhookConfig().getName(),
                    messageId.toString()
            );
        }
    }
    
    /**
     * Entrega un mensaje a su destino.
     * 
     * @param message Mensaje a entregar
     * @throws WebhookProcessingException Si ocurre error en la entrega
     */
    private void deliverMessage(Message message) {
        UUID messageId = message.getId();
        WebhookConfig config = message.getWebhookConfig();
        String webhookName = config.getName();
        String targetUrl = message.getTargetUrl();
        
        log.info("Enviando mensaje {} del webhook {} a URL: {}", 
                messageId, webhookName, targetUrl);
        
        // Crear intento de entrega
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.setMessage(message);
        attempt.setAttemptNumber(message.getRetryCount() + 1);
        attempt.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
        attempt.setTargetUrl(targetUrl);
        
        // Agregar identificador de nodo si está configurado (para deployments multi-instancia)
        if (nodeIdentifier != null && !nodeIdentifier.isEmpty()) {
            attempt.setProcessingNode(nodeIdentifier);
        }
        
        try {
            // Preparar headers
            HttpHeaders headers = prepareHeaders(message);
            
            // Loguear intento (truncando payload si es muy largo)
            String truncatedPayload = truncateForLog(message.getPayload());
            log.debug("Enviando payload: {} con headers: {}", truncatedPayload, headers);
            
            // Registrar tiempo de inicio
            long startTime = System.currentTimeMillis();
            
            // Realizar solicitud HTTP al destino
            org.springframework.http.HttpEntity<String> entity = 
                    new org.springframework.http.HttpEntity<>(message.getPayload(), headers);
            
            org.springframework.http.ResponseEntity<String> response = 
                    restTemplate.postForEntity(targetUrl, entity, String.class);
            
            // Calcular duración
            long duration = System.currentTimeMillis() - startTime;
            
            // Registrar respuesta
            attempt.setStatusCode(response.getStatusCode().value());
            attempt.setResponseBody(truncateResponse(response.getBody()));
            attempt.setRequestDuration(duration);
            attempt.setResponseHeaders(convertHeadersToJson(response.getHeaders()));
            
            // Guardar intento
            deliveryAttemptRepository.save(attempt);
            
            // Procesar resultado según código de estado
            if (response.getStatusCode().is2xxSuccessful()) {
                // Éxito - marcar como entregado
                log.info("Mensaje {} entregado exitosamente a {} en {} ms", 
                        messageId, targetUrl, duration);
                
                messageRepository.markAsDelivered(messageId);
                
                // Actualizar estadísticas de salud
                healthMonitoringService.recordSuccessfulDelivery(
                        config.getName(),  // Usar el nombre del webhook en lugar del ID
                        response.getStatusCode().value(),
                        duration
                );


            } else if (shouldRetry(response.getStatusCode().value())) {
                // Respuesta de error pero susceptible de reintento
                log.warn("Envío de mensaje {} falló con estado {}, se programará reintento", 
                        messageId, response.getStatusCode());
                
                // Programar reintento
                scheduleRetry(message, attempt, 
                        "Respuesta de error: HTTP " + response.getStatusCode().value());
                
                // Actualizar estadísticas de salud
                healthMonitoringService.recordFailedAttempt(
                        config.getId(),
                        response.getStatusCode().value(),
                        duration
                );
                
            } else {
                // Error permanente, no reintentar
                log.error("Envío de mensaje {} falló permanentemente con estado {}", 
                        messageId, response.getStatusCode());
                
                // Marcar como fallido sin reintento
                messageRepository.markAsFailed(
                        messageId,
                        "Error permanente: HTTP " + response.getStatusCode().value(),
                        null
                );
                
                // Actualizar estadísticas de salud
                healthMonitoringService.recordFailedDelivery(config.getName());
            }
            
        } catch (SocketTimeoutException e) {
            // Timeout de conexión o respuesta
            log.warn("Timeout conectando a {} para mensaje {}: {}", 
                    targetUrl, messageId, e.getMessage());
            
            attempt.setError("Timeout: " + e.getMessage());
            attempt.setRequestDuration((long) Math.max(connectionTimeoutMs, readTimeoutMs));
            deliveryAttemptRepository.save(attempt);
            
            // Programar reintento
            scheduleRetry(message, attempt, "Timeout de conexión");
            
            // Actualizar estadísticas
            healthMonitoringService.recordConnectionError(config.getId());
            
        } catch (Exception e) {
            // Otros errores
            log.error("Error enviando mensaje {} a {}: {}", 
                    messageId, targetUrl, e.getMessage(), e);
            
            attempt.setError(e.getMessage());
            attempt.setRequestDuration(System.currentTimeMillis() - System.currentTimeMillis());
            deliveryAttemptRepository.save(attempt);
            
            // Determinar si el error es recuperable
            if (isRecoverableError(e)) {
                // Programar reintento
                scheduleRetry(message, attempt, "Error recuperable: " + e.getClass().getSimpleName());
            } else {
                // Error permanente
                messageRepository.markAsFailed(
                        messageId,
                        "Error permanente: " + e.getMessage(),
                        null
                );
                
                healthMonitoringService.recordFailedDelivery(config.getId());
            }
        }
    }
    
    /**
     * Prepara los headers HTTP para envío.
     * 
     * @param message Mensaje con la información necesaria
     * @return Headers HTTP listos para envío
     */
    private HttpHeaders prepareHeaders(Message message) {
        HttpHeaders headers = new HttpHeaders();
        
        // Content-Type siempre JSON
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Agregar firma
        headers.add("X-Webhook-Signature", message.getSignature());
        
        // Agregar ID de mensaje para correlación
        headers.add("X-Webhook-ID", message.getId().toString());
        
        // Agregar reintento si es aplicable
        if (message.getRetryCount() > 0) {
            headers.add("X-Webhook-Retry-Count", String.valueOf(message.getRetryCount()));
        }
        
        // Agregar headers personalizados definidos en configuración
        if (message.getHeaders() != null && !message.getHeaders().isEmpty()) {
            try {
                Map<String, String> customHeaders = objectMapper.readValue(
                        message.getHeaders(),
                        objectMapper.getTypeFactory().constructMapType(
                                Map.class, String.class, String.class));
                
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    headers.add(entry.getKey(), entry.getValue());
                }
            } catch (IOException e) {
                log.warn("Error parseando headers personalizados para mensaje {}: {}", 
                        message.getId(), e.getMessage());
            }
        }
        
        return headers;
    }
    
    /**
     * Programa un reintento para un mensaje fallido.
     * 
     * @param message Mensaje a reintentar
     * @param attempt Intento fallido
     * @param errorMessage Mensaje de error a registrar
     */
    private void scheduleRetry(Message message, DeliveryAttempt attempt, String errorMessage) {
        UUID messageId = message.getId();
        WebhookConfig config = message.getWebhookConfig();
        
        // Verificar si ya excedió máximo de reintentos
        if (message.hasExceededMaxRetries()) {
            log.warn("Mensaje {} ha excedido el máximo de reintentos ({}), no se reintentará", 
                    messageId, message.getRetryCount());
            
            messageRepository.markAsFailed(
                    messageId,
                    errorMessage + " - Máximo de reintentos excedido",
                    null
            );
            
            return;
        }
        
        // Calcular tiempo de próximo reintento
        OffsetDateTime nextRetry = retryService.calculateNextRetryTime(
                message, 
                attempt, 
                config
        );
        
        // Registrar reintento
        messageRepository.markAsFailed(
                messageId,
                errorMessage,
                nextRetry
        );
        
        log.info("Mensaje {} programado para reintento en: {}", messageId, nextRetry);
    }
    
    /**
     * Determina si un código de estado HTTP amerita reintento.
     * 
     * @param statusCode Código de estado HTTP
     * @return true si debería reintentarse, false si no
     */
    private boolean shouldRetry(int statusCode) {
        // Reintentar códigos 5xx (errores de servidor)
        if (statusCode >= 500 && statusCode < 600) {
            return true;
        }
        
        // Reintentar algunos códigos 4xx específicos
        return statusCode == 408 || // Request Timeout
               statusCode == 429 || // Too Many Requests
               statusCode == 423 || // Locked
               statusCode == 425 || // Too Early
               statusCode == 449 || // Retry With
               statusCode == 503;   // Service Unavailable
    }
    
    /**
     * Determina si un error es recuperable (amerita reintento).
     * 
     * @param e Excepción a analizar
     * @return true si debería reintentarse, false si no
     */
    private boolean isRecoverableError(Exception e) {
        // Errores de red/conexión son generalmente recuperables
        return e instanceof java.net.ConnectException ||
               e instanceof java.net.UnknownHostException ||
               e instanceof java.net.SocketTimeoutException ||
               e instanceof java.io.IOException ||
               e instanceof org.springframework.web.client.ResourceAccessException;
    }
    
    /**
     * Trunca un payload para log si es muy largo.
     * 
     * @param payload Payload completo
     * @return Payload truncado si necesario
     */
    private String truncateForLog(String payload) {
        if (payload == null) {
            return "null";
        }
        
        if (payload.length() <= maxPayloadLogLength) {
            return payload;
        }
        
        return payload.substring(0, maxPayloadLogLength) + "... [truncado]";
    }
    
    /**
     * Trunca respuesta si es muy larga.
     * 
     * @param response Cuerpo de respuesta
     * @return Respuesta truncada si necesario
     */
    private String truncateResponse(String response) {
        return DeliveryAttempt.truncateResponse(response, maxPayloadLogLength);
    }
    
    /**
     * Convierte headers HTTP a JSON para almacenamiento.
     * 
     * @param headers Headers HTTP
     * @return Representación JSON
     */
    private String convertHeadersToJson(HttpHeaders headers) {
        try {
            Map<String, String> headerMap = new HashMap<>();
            
            // Convertir MultiValueMap a Map simple con valores concatenados
            headers.forEach((name, values) -> {
                if (!values.isEmpty()) {
                    headerMap.put(name, String.join(", ", values));
                }
            });
            
            return objectMapper.writeValueAsString(headerMap);
        } catch (Exception e) {
            log.warn("Error convirtiendo headers a JSON: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Obtiene un mensaje por ID.
     * 
     * @param messageId ID del mensaje
     * @return Mensaje encontrado
     * @throws ResourceNotFoundException Si no existe
     */
    private Message getMessage(UUID messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mensaje no encontrado: " + messageId,
                        "message",
                        messageId.toString()
                ));
    }
    
    /**
     * Busca mensajes con criterios específicos.
     * 
     * @param webhookName Nombre del webhook (opcional)
     * @param status Estado (opcional)
     * @param fromDate Fecha desde (opcional)
     * @param toDate Fecha hasta (opcional)
     * @param pageable Configuración de paginación
     * @return Página de mensajes que coinciden con los criterios
     */
    @Transactional(readOnly = true)
    public Page<MessageDto> searchMessages(
            String webhookName,
            String status,
            String fromDate,
            String toDate,
            Pageable pageable) {
        
        // Convertir string de status a enum si existe
        MessageStatus statusEnum = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusEnum = MessageStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Estado inválido en búsqueda: {}", status);
            }
        }
        
        // Convertir fechas si existen
        OffsetDateTime fromDateTime = null;
        if (fromDate != null && !fromDate.isEmpty()) {
            try {
                fromDateTime = OffsetDateTime.parse(fromDate);
            } catch (Exception e) {
                log.warn("Formato de fecha 'desde' inválido: {}", fromDate);
            }
        }
        
        OffsetDateTime toDateTime = null;
        if (toDate != null && !toDate.isEmpty()) {
            try {
                toDateTime = OffsetDateTime.parse(toDate);
            } catch (Exception e) {
                log.warn("Formato de fecha 'hasta' inválido: {}", toDate);
            }
        }
        
        // Realizar búsqueda
        Page<Message> messages = messageRepository.searchMessages(
                webhookName,
                statusEnum,
                fromDateTime,
                toDateTime,
                pageable
        );
        
        // Convertir a DTOs
        return messages.map(this::convertToDto);
    }
    
    /**
     * Convierte un mensaje a DTO.
     * 
     * @param message Entidad Message
     * @return DTO correspondiente
     */
    private MessageDto convertToDto(Message message) {
        MessageDto dto = new MessageDto();
        
        dto.setId(message.getId());
        dto.setWebhookConfigId(message.getWebhookConfig().getId());
        dto.setWebhookName(message.getWebhookConfig().getName());
        dto.setTargetUrl(message.getTargetUrl());
        dto.setStatus(message.getStatus().name());
        dto.setSignature(message.getSignature());
        dto.setRetryCount(message.getRetryCount());
        
        if (message.getNextRetry() != null) {
            dto.setNextRetry(message.getNextRetry().toString());
        }
        
        dto.setLastError(message.getLastError());
        dto.setCreatedAt(message.getCreatedAt().toString());
        dto.setUpdatedAt(message.getUpdatedAt().toString());
        
        // Convertir intentos si están cargados
        if (message.getDeliveryAttempts() != null && !message.getDeliveryAttempts().isEmpty()) {
            dto.setDeliveryAttempts(message.getDeliveryAttempts().stream()
                    .map(this::convertAttemptToDto)
                    .collect(Collectors.toList()));
        }
        
        // Agregar payload solo si se solicita explícitamente para evitar transferencia de datos grande
        // dto.setPayload(message.getPayload());
        
        return dto;
    }
    
    /**
     * Convierte un intento de entrega a DTO.
     * 
     * @param attempt Entidad DeliveryAttempt
     * @return DTO correspondiente
     */
    private DeliveryAttemptDto convertAttemptToDto(DeliveryAttempt attempt) {
        DeliveryAttemptDto dto = new DeliveryAttemptDto();
        
        dto.setId(attempt.getId());
        dto.setAttemptNumber(attempt.getAttemptNumber());
        dto.setTimestamp(attempt.getTimestamp().toString());
        dto.setStatusCode(attempt.getStatusCode());
        dto.setResponseBody(attempt.getResponseBody());
        dto.setError(attempt.getError());
        dto.setRequestDuration(attempt.getRequestDuration());
        dto.setTargetUrl(attempt.getTargetUrl());
        
        return dto;
    }
    
    // Otros métodos del servicio como getMessageById, getDeliveryAttempts, etc.
    // omitidos por brevedad
}