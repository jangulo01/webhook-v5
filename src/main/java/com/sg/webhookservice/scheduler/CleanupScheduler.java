package com.sg.webhookservice.scheduler;

import com.yourcompany.webhookservice.model.Message.MessageStatus;
import com.yourcompany.webhookservice.repository.DeliveryAttemptRepository;
import com.yourcompany.webhookservice.repository.MessageRepository;
import com.yourcompany.webhookservice.repository.WebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Componente que programa y ejecuta tareas de limpieza y mantenimiento
 * automáticas para mantener el sistema optimizado y eliminar datos obsoletos.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CleanupScheduler {

    private final MessageRepository messageRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    
    // Configuración de retención de datos
    @Value("${cleanup.delivered-messages-retention-days:30}")
    private int deliveredMessagesRetentionDays;
    
    @Value("${cleanup.failed-messages-retention-days:90}")
    private int failedMessagesRetentionDays;
    
    @Value("${cleanup.cancelled-messages-retention-days:15}")
    private int cancelledMessagesRetentionDays;
    
    @Value("${cleanup.delivery-attempts-retention-days:30}")
    private int deliveryAttemptsRetentionDays;
    
    @Value("${cleanup.clean-batch-size:1000}")
    private int cleanBatchSize;
    
    @Value("${cleanup.enabled:true}")
    private boolean cleanupEnabled;
    
    /**
     * Tarea programada para limpiar mensajes antiguos de entrega exitosa
     * Ejecutada todos los días a las 2:00 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldDeliveredMessages() {
        if (!cleanupEnabled) {
            log.info("Limpieza de mensajes entregados desactivada por configuración");
            return;
        }
        
        log.info("Iniciando limpieza de mensajes entregados antiguos...");
        OffsetDateTime cutoffDate = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(deliveredMessagesRetentionDays);
        
        int deleted = messageRepository.deleteOldMessages(
                cutoffDate, 
                List.of(MessageStatus.DELIVERED)
        );
        
        log.info("Limpieza completada: {} mensajes entregados antiguos eliminados", deleted);
    }
    
    /**
     * Tarea programada para limpiar mensajes antiguos de entrega fallida
     * Ejecutada todos los domingos a las 3:00 AM
     */
    @Scheduled(cron = "0 0 3 * * 0")
    @Transactional
    public void cleanupOldFailedMessages() {
        if (!cleanupEnabled) {
            log.info("Limpieza de mensajes fallidos desactivada por configuración");
            return;
        }
        
        log.info("Iniciando limpieza de mensajes fallidos antiguos...");
        OffsetDateTime cutoffDate = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(failedMessagesRetentionDays);
        
        int deleted = messageRepository.deleteOldMessages(
                cutoffDate, 
                List.of(MessageStatus.FAILED)
        );
        
        log.info("Limpieza completada: {} mensajes fallidos antiguos eliminados", deleted);
    }
    
    /**
     * Tarea programada para limpiar mensajes antiguos cancelados
     * Ejecutada todos los días a las 2:30 AM
     */
    @Scheduled(cron = "0 30 2 * * ?")
    @Transactional
    public void cleanupOldCancelledMessages() {
        if (!cleanupEnabled) {
            log.info("Limpieza de mensajes cancelados desactivada por configuración");
            return;
        }
        
        log.info("Iniciando limpieza de mensajes cancelados antiguos...");
        OffsetDateTime cutoffDate = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(cancelledMessagesRetentionDays);
        
        int deleted = messageRepository.deleteOldMessages(
                cutoffDate, 
                List.of(MessageStatus.CANCELLED)
        );
        
        log.info("Limpieza completada: {} mensajes cancelados antiguos eliminados", deleted);
    }
    
    /**
     * Tarea programada para limpiar intentos de entrega antiguos de mensajes entregados
     * Ejecutada todos los lunes a las 4:00 AM
     */
    @Scheduled(cron = "0 0 4 * * 1")
    @Transactional
    public void cleanupOldDeliveryAttempts() {
        if (!cleanupEnabled) {
            log.info("Limpieza de intentos de entrega desactivada por configuración");
            return;
        }
        
        log.info("Iniciando limpieza de intentos de entrega antiguos...");
        
        int deleted = deliveryAttemptRepository.cleanupOldDeliveryAttempts(deliveryAttemptsRetentionDays);
        
        log.info("Limpieza completada: {} intentos de entrega antiguos eliminados", deleted);
    }
    
    /**
     * Tarea programada para detectar y resolver mensajes "stuck" (colgados en PROCESSING)
     * Ejecutada cada 15 minutos
     */
    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void detectAndResolveStuckMessages() {
        log.info("Detectando mensajes colgados en estado PROCESSING...");
        
        // Considerar colgado si lleva más de 30 minutos en PROCESSING
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30);
        
        List<UUID> stuckMessageIds = messageRepository.findStuckMessages(threshold)
                .stream()
                .map(Message::getId)
                .toList();
        
        if (stuckMessageIds.isEmpty()) {
            log.info("No se encontraron mensajes colgados");
            return;
        }
        
        log.warn("Se encontraron {} mensajes colgados en PROCESSING", stuckMessageIds.size());
        AtomicInteger resolvedCount = new AtomicInteger(0);
        
        // Procesar en lotes para evitar transacciones muy grandes
        Lists.partition(stuckMessageIds, cleanBatchSize).forEach(batch -> {
            batch.forEach(messageId -> {
                try {
                    // Marcar como fallido con reintento
                    OffsetDateTime nextRetry = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
                    messageRepository.markAsFailed(
                            messageId,
                            "Mensaje automáticamente recuperado de estado colgado",
                            nextRetry
                    );
                    resolvedCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Error al resolver mensaje colgado {}: {}", messageId, e.getMessage(), e);
                }
            });
        });
        
        log.info("Resolución completada: {} mensajes colgados recuperados", resolvedCount.get());
    }
    
    /**
     * Tarea programada para limpiar webhooks no utilizados
     * Ejecutada el primer día de cada mes a las 5:00 AM
     */
    @Scheduled(cron = "0 0 5 1 * ?")
    public void reportUnusedWebhooks() {
        log.info("Buscando webhooks no utilizados...");
        
        // Configuraciones creadas hace más de 90 días sin mensajes asociados
        OffsetDateTime cutoffDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(90);
        
        List<WebhookConfig> unusedWebhooks = webhookConfigRepository
                .findUnusedWebhooks(cutoffDate, PageRequest.of(0, 100))
                .getContent();
        
        if (unusedWebhooks.isEmpty()) {
            log.info("No se encontraron webhooks no utilizados");
            return;
        }
        
        log.info("Se encontraron {} webhooks no utilizados:", unusedWebhooks.size());
        unusedWebhooks.forEach(config -> 
            log.info("  - {} (creado: {})", config.getName(), config.getCreatedAt())
        );
        
        // No los eliminamos automáticamente, solo reportamos para revisión manual
    }
    
    /**
     * Tarea programada para registrar estadísticas generales del sistema
     * Ejecutada todos los días a medianoche
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void logSystemStats() {
        log.info("Generando estadísticas del sistema...");
        
        // Contar mensajes por estado
        List<Object[]> statusCounts = messageRepository.countByStatus();
        Map<String, Long> messagesByStatus = statusCounts.stream()
                .collect(Collectors.toMap(
                        row -> ((MessageStatus) row[0]).name(),
                        row -> (Long) row[1]
                ));
        
        // Contar webhooks por estrategia
        List<Object[]> strategyCounts = webhookConfigRepository.countByBackoffStrategy();
        Map<String, Long> webhooksByStrategy = strategyCounts.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], 
                        row -> (Long) row[1]
                ));
        
        // Registrar estadísticas
        log.info("Estadísticas de mensajes por estado: {}", messagesByStatus);
        log.info("Estadísticas de webhooks por estrategia: {}", webhooksByStrategy);
        
        // Otras estadísticas potenciales (implementación completa agregaría más métricas)
    }
}