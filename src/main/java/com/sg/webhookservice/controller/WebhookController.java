package com.sg.webhookservice.controller;

import com.yourcompany.webhookservice.dto.MessageDto;
import com.yourcompany.webhookservice.dto.WebhookRequestDto;
import com.yourcompany.webhookservice.exception.ResourceNotFoundException;
import com.yourcompany.webhookservice.exception.WebhookProcessingException;
import com.yourcompany.webhookservice.service.WebhookService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador principal para recepción de webhooks.
 * Proporciona endpoints para recibir payloads de webhook 
 * y ponerlos en cola para procesamiento.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Endpoints para recepción y procesamiento de webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * Endpoint para recibir webhooks y ponerlos en cola para entrega
     */
    @PostMapping(
        path = "/webhook/{webhookName}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "Recibe un webhook evento",
        description = "Acepta un payload JSON, lo valida contra la configuración del webhook " +
                      "especificado y lo pone en cola para entrega. La URL real del webhook depende " +
                      "de la configuración del webhook.",
        responses = {
            @ApiResponse(
                responseCode = "202",
                description = "Webhook recibido y puesto en cola correctamente",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = WebhookReceiptResponse.class)
                )
            ),
            @ApiResponse(responseCode = "400", description = "Payload inválido o mal formado"),
            @ApiResponse(responseCode = "404", description = "Configuración de webhook no encontrada"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor")
        }
    )
    @Timed(value = "webhook.receive", description = "Tiempo para procesar la recepción de webhook")
    public ResponseEntity<WebhookReceiptResponse> receiveWebhook(
            @Parameter(description = "Nombre del webhook a invocar", required = true)
            @PathVariable String webhookName,
            
            @Parameter(description = "Payload del webhook", required = true)
            @Validated @RequestBody WebhookRequestDto requestPayload,
            
            HttpServletRequest request) {
        
        try {
            log.info("Recibiendo webhook para configuración: {}", webhookName);
            
            // Procesar el webhook
            MessageDto message = webhookService.receiveWebhook(
                    webhookName, 
                    requestPayload.getData(),
                    extractClientInfo(request)
            );
            
            // Construir respuesta
            WebhookReceiptResponse response = new WebhookReceiptResponse(
                    message.getId().toString(),
                    message.getStatus().toLowerCase(),
                    "Webhook recibido y puesto en cola correctamente"
            );
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (ResourceNotFoundException e) {
            // Webhook no encontrado
            log.warn("Configuración de webhook no encontrada: {}", webhookName);
            throw e;
        } catch (Exception e) {
            // Error general
            log.error("Error procesando webhook {}: {}", webhookName, e.getMessage(), e);
            throw new WebhookProcessingException("Error procesando webhook: " + e.getMessage(), e);
        }
    }

    /**
     * Endpoint auxiliar para verificar el estado de un mensaje
     */
    @GetMapping("/webhook/message/{messageId}")
    @Operation(
        summary = "Verifica el estado de un mensaje de webhook",
        description = "Proporciona información sobre el estado actual de un mensaje de webhook específico."
    )
    @Timed(value = "webhook.message.status", description = "Tiempo para verificar estado de mensaje")
    public ResponseEntity<MessageStatusResponse> getMessageStatus(
            @Parameter(description = "ID del mensaje a verificar", required = true)
            @PathVariable String messageId) {
        
        try {
            UUID id = UUID.fromString(messageId);
            MessageDto message = webhookService.getMessageStatus(id);
            
            MessageStatusResponse response = new MessageStatusResponse(
                    message.getId().toString(),
                    message.getWebhookName(),
                    message.getStatus().toLowerCase(),
                    message.getRetryCount(),
                    message.getCreatedAt(),
                    message.getUpdatedAt(),
                    message.getNextRetry(),
                    message.getLastError(),
                    message.getDeliveryAttempts().size()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Formato de ID inválido: " + messageId);
        }
    }

    /**
     * Extrae información del cliente de la solicitud HTTP
     */
    private Map<String, String> extractClientInfo(HttpServletRequest request) {
        Map<String, String> clientInfo = new HashMap<>();
        
        // Obtener IP del cliente (considerando posibles proxies)
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        } else if (clientIp.contains(",")) {
            // Si hay múltiples IPs en X-Forwarded-For, usar la primera (cliente original)
            clientIp = clientIp.split(",")[0].trim();
        }
        
        clientInfo.put("ip", clientIp);
        clientInfo.put("userAgent", request.getHeader("User-Agent"));
        
        // Otras cabeceras que podrían ser útiles
        String contentType = request.getHeader("Content-Type");
        if (contentType != null) {
            clientInfo.put("contentType", contentType);
        }
        
        return clientInfo;
    }

    /**
     * Clase interna para respuesta de recepción de webhook
     */
    @Schema(description = "Respuesta de recepción de webhook")
    public static class WebhookReceiptResponse {
        @Schema(description = "ID único del mensaje creado", example = "123e4567-e89b-12d3-a456-426614174000")
        private final String messageId;
        
        @Schema(description = "Estado inicial del mensaje", example = "pending")
        private final String status;
        
        @Schema(description = "Mensaje informativo", example = "Webhook recibido y puesto en cola correctamente")
        private final String message;

        public WebhookReceiptResponse(String messageId, String status, String message) {
            this.messageId = messageId;
            this.status = status;
            this.message = message;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Clase interna para respuesta de estado de mensaje
     */
    @Schema(description = "Estado de un mensaje de webhook")
    public static class MessageStatusResponse {
        @Schema(description = "ID único del mensaje", example = "123e4567-e89b-12d3-a456-426614174000")
        private final String messageId;
        
        @Schema(description = "Nombre del webhook asociado", example = "payment-notification")
        private final String webhookName;
        
        @Schema(description = "Estado actual del mensaje", example = "delivered")
        private final String status;
        
        @Schema(description = "Número de reintentos realizados", example = "2")
        private final int retryCount;
        
        @Schema(description = "Fecha de creación", example = "2023-01-01T12:00:00Z")
        private final String createdAt;
        
        @Schema(description = "Fecha de última actualización", example = "2023-01-01T12:05:30Z")
        private final String updatedAt;
        
        @Schema(description = "Fecha programada para próximo reintento", example = "2023-01-01T12:30:00Z")
        private final String nextRetry;
        
        @Schema(description = "Último error registrado", example = "Connection timeout")
        private final String lastError;
        
        @Schema(description = "Número de intentos de entrega", example = "3")
        private final int deliveryAttempts;

        public MessageStatusResponse(
                String messageId, 
                String webhookName, 
                String status, 
                int retryCount, 
                String createdAt, 
                String updatedAt, 
                String nextRetry, 
                String lastError, 
                int deliveryAttempts) {
            this.messageId = messageId;
            this.webhookName = webhookName;
            this.status = status;
            this.retryCount = retryCount;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.nextRetry = nextRetry;
            this.lastError = lastError;
            this.deliveryAttempts = deliveryAttempts;
        }

        public String getMessageId() {
            return messageId;
        }

        public String getWebhookName() {
            return webhookName;
        }

        public String getStatus() {
            return status;
        }

        public int getRetryCount() {
            return retryCount;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public String getNextRetry() {
            return nextRetry;
        }

        public String getLastError() {
            return lastError;
        }

        public int getDeliveryAttempts() {
            return deliveryAttempts;
        }
    }
}