package com.sg.webhookservice.controller;

import com.sg.webhookservice.dto.BulkRetryRequest;
import com.sg.webhookservice.dto.DirectSendRequest;
import com.sg.webhookservice.dto.HealthCheckResponse;
import com.sg.webhookservice.dto.MessageDto;
import com.sg.webhookservice.service.HealthMonitoringService;
import com.sg.webhookservice.service.MessageProcessingService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador que proporciona endpoints administrativos para gestionar
 * y monitorear el servicio de webhooks.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Endpoints administrativos para gestión y monitoreo del servicio de webhooks")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final MessageProcessingService messageProcessingService;
    private final HealthMonitoringService healthMonitoringService;

    /**
     * Verifica el estado de salud del servicio
     */
    @GetMapping("/health")
    @Operation(summary = "Comprueba el estado de salud detallado del servicio",
               description = "Proporciona información detallada sobre la salud del servicio, " +
                             "incluyendo conexiones a DB, Kafka y más.")
    @Timed(value = "admin.health.check", description = "Tiempo para realizar health check")
    public ResponseEntity<HealthCheckResponse> healthCheck() {
        HealthCheckResponse health = healthMonitoringService.getDetailedHealthStatus();
        return ResponseEntity.ok(health);
    }

    /**
     * Busca y procesa mensajes pendientes manualmente
     */
    @PostMapping("/check-pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Busca y procesa mensajes pendientes",
               description = "Desencadena manualmente un proceso para verificar mensajes pendientes " +
                             "en la base de datos y Kafka, y los procesa.")
    @Timed(value = "admin.check.pending", description = "Tiempo para verificar mensajes pendientes")
    public ResponseEntity<Map<String, Object>> checkPendingMessages() {
        int totalProcessed = messageProcessingService.processPendingMessages();
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "pending_messages_processed", totalProcessed,
            "message", String.format("Encontrados y procesados %d mensajes pendientes", totalProcessed)
        ));
    }

    /**
     * Envía directamente mensajes pendientes
     */
    @PostMapping("/direct-send")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Envía directamente mensajes pendientes",
               description = "Busca mensajes con status específico y los envía directamente, " +
                             "omitiendo Kafka y el procesamiento normal.")
    @Timed(value = "admin.direct.send", description = "Tiempo para envío directo")
    public ResponseEntity<Map<String, Object>> directSend(
            @Valid @RequestBody DirectSendRequest request) {
        
        log.info("Iniciando envío directo de mensajes. Status: {}, Limit: {}", 
                request.getStatus(), request.getLimit());
        
        Map<String, Object> results = messageProcessingService.directSendMessages(
                request.getStatus(), 
                request.getLimit(), 
                request.getDestinationUrl(),
                request.getSecret()
        );
        
        return ResponseEntity.ok(results);
    }

    /**
     * Reintenta enviar mensajes fallidos en lote
     */
    @PostMapping("/bulk-retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reintenta mensajes fallidos en lote",
               description = "Busca mensajes fallidos dentro de un período específico y reintenta su envío.")
    @Timed(value = "admin.bulk.retry", description = "Tiempo para reintento en lote")
    public ResponseEntity<Map<String, Object>> bulkRetry(
            @Valid @RequestBody BulkRetryRequest request) {
        
        log.info("Iniciando reintento en lote. Horas: {}, Limit: {}", 
                request.getHours(), request.getLimit());
        
        Map<String, Object> results = messageProcessingService.bulkRetryFailedMessages(
                request.getHours(),
                request.getLimit(),
                request.getDestinationUrl()
        );
        
        return ResponseEntity.ok(results);
    }

    /**
     * Obtiene estadísticas de mensajes por estado
     */
    @GetMapping("/stats")
    @Operation(summary = "Obtiene estadísticas de mensajes",
               description = "Proporciona un resumen estadístico de los mensajes por estado.")
    @Timed(value = "admin.stats", description = "Tiempo para obtener estadísticas")
    public ResponseEntity<Map<String, Object>> getMessageStats() {
        Map<String, Object> stats = messageProcessingService.getMessageStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Borra mensajes antiguos para mantenimiento
     */
    @DeleteMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Limpia mensajes antiguos",
               description = "Elimina mensajes antiguos para mantenimiento de la base de datos.")
    @Timed(value = "admin.cleanup", description = "Tiempo para limpieza")
    public ResponseEntity<Map<String, Object>> cleanupOldMessages(
            @Parameter(description = "Días de antigüedad para eliminar (por defecto: 30)")
            @RequestParam(defaultValue = "30") int days) {
        
        int deletedCount = messageProcessingService.cleanupOldMessages(days);
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "deleted_count", deletedCount,
            "message", String.format("Se eliminaron %d mensajes antiguos", deletedCount)
        ));
    }

    /**
     * Provoca un reenvío específico de un mensaje
     */
    @PostMapping("/resend/{messageId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reenvía un mensaje específico",
               description = "Fuerza el reenvío de un mensaje específico por su ID.")
    @Timed(value = "admin.resend", description = "Tiempo para reenvío específico")
    public ResponseEntity<MessageDto> resendMessage(
            @PathVariable String messageId,
            @RequestParam(required = false) String destinationUrl) {
        
        MessageDto result = messageProcessingService.forceResendMessage(messageId, destinationUrl);
        return ResponseEntity.ok(result);
    }
}