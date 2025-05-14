package com.sg.webhookservice.service.impl;

import com.sg.webhookservice.domain.entity.Message;
import com.sg.webhookservice.domain.entity.WebhookConfig;
import com.sg.webhookservice.domain.entity.WebhookHealthStats;
import com.sg.webhookservice.domain.repository.MessageRepository;
import com.sg.webhookservice.domain.repository.WebhookConfigRepository;
import com.sg.webhookservice.domain.repository.WebhookHealthStatsRepository;
import com.sg.webhookservice.presentation.dto.HealthResponseDto;
import com.sg.webhookservice.presentation.dto.WebhookHealthStatsDto;
import com.sg.webhookservice.presentation.exception.ResourceNotFoundException;
import com.sg.webhookservice.presentation.mapper.WebhookHealthStatsMapper;
import com.sg.webhookservice.service.HealthMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de monitoreo de salud.
 * Proporciona funcionalidad para supervisar el estado del sistema y los webhooks.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HealthMonitoringServiceImpl implements HealthMonitoringService {

    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookHealthStatsRepository webhookHealthStatsRepository;
    private final WebhookHealthStatsMapper webhookHealthStatsMapper;
    private final MessageRepository messageRepository;

    @Value("${webhook.health.minimum-success-rate:80.0}")
    private double minimumSuccessRate;

    @Value("${webhook.health.minimum-sent-messages:5}")
    private int minimumSentMessages;

    @Value("${application.version:1.0.0}")
    private String applicationVersion;

    @Value("${spring.datasource.url:}")
    private String databaseUrl;

    @Value("${spring.datasource.hikari.schema:}")
    private String databaseSchema;

    @Value("${kafka.bootstrap-servers:}")
    private String kafkaServers;

    @Value("${webhook.delivery.mode:direct}")
    private String deliveryMode;

    @Value("${webhook.destination-url-override:}")
    private String destinationUrlOverride;

    @Value("${webhook.hmac.enabled:true}")
    private boolean hmacEnabled;

    @Override
    @Transactional(readOnly = true)
    public HealthResponseDto getServiceHealth() {
        // Construir respuesta de salud del servicio usando el DTO proporcionado
        HealthResponseDto healthResponse = new HealthResponseDto();

        // Información básica del servicio
        healthResponse.setVersion(applicationVersion);

        // Información de conexión
        healthResponse.setDatabase(databaseUrl);
        healthResponse.setSchema(databaseSchema);
        healthResponse.setKafka(kafkaServers.isEmpty() ? "unavailable" : kafkaServers);

        // Modo de operación
        healthResponse.setMode(deliveryMode);

        // URL de override si está configurada
        if (!destinationUrlOverride.isEmpty()) {
            healthResponse.setDestinationUrlOverride(destinationUrlOverride);
        }

        // Estado del módulo HMAC
        healthResponse.setHmacModule(hmacEnabled ? "available" : "unavailable");

        // Información de estadísticas
        List<WebhookConfig> activeWebhooksList = webhookConfigRepository.findByActiveTrue();
        healthResponse.setActiveWebhooks(activeWebhooksList.size());

        // Contar mensajes pendientes
        int pendingMessages = messageRepository.findByStatus(Message.MessageStatus.PENDING).size();
        healthResponse.setPendingMessages(pendingMessages);

        // Calcular tiempo de actividad
        long uptimeInSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        healthResponse.setUptime(uptimeInSeconds / 3600.0); // Convertir a horas

        // Determinar estado general basado en webhooks no saludables
        List<WebhookHealthStatsDto> unhealthyWebhooks = getUnhealthyWebhooks(minimumSuccessRate);

        // Lógica de determinación de estado según condiciones de HealthResponseDto
        if ("unavailable".equals(healthResponse.getKafka()) && "kafka".equals(healthResponse.getMode())) {
            healthResponse.setStatus("unhealthy");
        } else if (pendingMessages > 1000 || !unhealthyWebhooks.isEmpty()) {
            healthResponse.setStatus("degraded");
        } else {
            healthResponse.setStatus("healthy");
        }

        return healthResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookHealthStatsDto getWebhookHealthStats(UUID webhookId) {
        // Verificar que exista el webhook
        if (!webhookConfigRepository.existsById(webhookId)) {
            throw new ResourceNotFoundException("Webhook configuration not found", "webhook", webhookId.toString());
        }

        // Buscar estadísticas
        WebhookHealthStats stats = webhookHealthStatsRepository.findByWebhookConfigId(webhookId)
                .orElseThrow(() -> new ResourceNotFoundException("Health statistics not found", "webhook_health_stats", webhookId.toString()));

        return webhookHealthStatsMapper.toDto(stats);
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookHealthStatsDto getWebhookHealthStatsByName(String webhookName) {
        // Buscar estadísticas por nombre
        WebhookHealthStats stats = webhookHealthStatsRepository.findByWebhookName(webhookName)
                .orElseThrow(() -> new ResourceNotFoundException("Health statistics not found", "webhook_health_stats", webhookName));

        return webhookHealthStatsMapper.toDto(stats);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookHealthStatsDto> getAllWebhookHealthStats() {
        // Obtener todas las estadísticas de salud ordenadas por tasa de éxito
        List<WebhookHealthStats> statsList = webhookHealthStatsRepository.findAllOrderBySuccessRate();
        return webhookHealthStatsMapper.toDtoList(statsList);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebhookHealthStatsDto> getUnhealthyWebhooks(double minSuccess) {
        // Obtener webhooks con problemas de salud
        List<WebhookHealthStats> unhealthyWebhooks = webhookHealthStatsRepository.findUnhealthyWebhooks(
                minimumSentMessages, minSuccess);
        return webhookHealthStatsMapper.toDtoList(unhealthyWebhooks);
    }

    @Override
    @Transactional
    public void recordSuccessfulDelivery(UUID webhookId, int statusCode, long responseTimeMs) {
        log.debug("Recording successful delivery for webhook {}: status={}, time={}ms",
                webhookId, statusCode, responseTimeMs);

        try {
            int updated = webhookHealthStatsRepository.recordSuccessfulDelivery(webhookId, responseTimeMs);

            // Si no se actualizó ninguna fila, puede que no existan las estadísticas
            if (updated == 0) {
                createMissingHealthStats(webhookId);
                webhookHealthStatsRepository.recordSuccessfulDelivery(webhookId, responseTimeMs);
            }
        } catch (Exception e) {
            log.error("Error recording successful delivery for webhook {}: {}", webhookId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void recordFailedDelivery(UUID webhookId) {
        recordFailedDelivery(webhookId, "Unknown error");
    }

    @Override
    @Transactional
    public void recordFailedDelivery(UUID webhookId, String errorMessage) {
        log.debug("Recording failed delivery for webhook {}: {}", webhookId, errorMessage);

        try {
            int updated = webhookHealthStatsRepository.recordFailedDelivery(webhookId, errorMessage);

            // Si no se actualizó ninguna fila, puede que no existan las estadísticas
            if (updated == 0) {
                createMissingHealthStats(webhookId);
                webhookHealthStatsRepository.recordFailedDelivery(webhookId, errorMessage);
            }
        } catch (Exception e) {
            log.error("Error recording failed delivery for webhook {}: {}", webhookId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void recordConnectionError(UUID webhookId) {
        recordFailedDelivery(webhookId, "Connection error");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getGlobalStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Obtener estadísticas globales
        Object[] globalStats = webhookHealthStatsRepository.getGlobalStats();

        // Si hay estadísticas disponibles
        if (globalStats != null && globalStats.length >= 4) {
            Long totalSent = (Long) globalStats[0];
            Long totalDelivered = (Long) globalStats[1];
            Long totalFailed = (Long) globalStats[2];
            Double avgResponseTime = (Double) globalStats[3];

            stats.put("totalSent", totalSent != null ? totalSent : 0);
            stats.put("totalDelivered", totalDelivered != null ? totalDelivered : 0);
            stats.put("totalFailed", totalFailed != null ? totalFailed : 0);
            stats.put("avgResponseTime", avgResponseTime != null ? avgResponseTime : 0.0);

            // Calcular tasa de éxito global
            double successRate = 0.0;
            if (totalSent != null && totalSent > 0 && totalDelivered != null) {
                successRate = (double) totalDelivered / totalSent * 100;
            }
            stats.put("successRate", Math.round(successRate * 100.0) / 100.0); // Redondear a 2 decimales
        } else {
            // Sin datos, proporcionar valores predeterminados
            stats.put("totalSent", 0);
            stats.put("totalDelivered", 0);
            stats.put("totalFailed", 0);
            stats.put("avgResponseTime", 0.0);
            stats.put("successRate", 0.0);
        }

        // Obtener conteos
        stats.put("webhooksCount", webhookConfigRepository.findAll().size());
        stats.put("activeWebhooksCount", webhookConfigRepository.findByActiveTrue().size());

        // Contar mensajes por estado
        stats.put("pendingMessages", messageRepository.findByStatus(Message.MessageStatus.PENDING).size());
        stats.put("failedMessages", messageRepository.findByStatus(Message.MessageStatus.FAILED).size());

        // Estado del sistema
        stats.put("systemStatus", "healthy");
        stats.put("lastChecked", OffsetDateTime.now().toString());

        return stats;
    }

    @Override
    @Transactional
    public int performHealthCheck() {
        log.info("Performing health check for all webhooks");

        // Obtener todos los webhooks configurados
        List<WebhookConfig> configs = webhookConfigRepository.findAll();
        int checkedCount = 0;

        for (WebhookConfig config : configs) {
            try {
                // Obtener estadísticas de salud actuales
                Optional<WebhookHealthStats> statsOpt = webhookHealthStatsRepository.findByWebhookConfigId(config.getId());

                // Si no existen estadísticas para este webhook, crearlas
                if (statsOpt.isEmpty()) {
                    createMissingHealthStats(config.getId());
                }

                // Actualizar tiempo de comprobación
                // (en una implementación real, aquí se podrían realizar pruebas
                // de conexión o verificación de firmas, etc.)

                checkedCount++;
            } catch (Exception e) {
                log.error("Error checking health for webhook {}: {}", config.getName(), e.getMessage(), e);
            }
        }

        log.info("Health check completed for {} webhooks", checkedCount);
        return checkedCount;
    }

    /**
     * Crea estadísticas de salud para un webhook si no existen.
     *
     * @param webhookId ID del webhook
     */
    private void createMissingHealthStats(UUID webhookId) {
        WebhookConfig config = webhookConfigRepository.findById(webhookId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook configuration not found", "webhook", webhookId.toString()));

        // Verificar si ya existen estadísticas
        if (webhookHealthStatsRepository.findByWebhookConfigId(webhookId).isPresent()) {
            return;
        }

        // Crear estadísticas iniciales
        WebhookHealthStats stats = WebhookHealthStats.createInitial(config);
        webhookHealthStatsRepository.save(stats);

        log.info("Created initial health stats for webhook: {}", config.getName());
    }
}