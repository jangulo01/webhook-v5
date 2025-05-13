package com.sg.webhookservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad que almacena estadísticas de salud y rendimiento para una configuración de webhook.
 * 
 * Mantiene métricas agregadas como tasas de éxito, tiempos de respuesta,
 * contadores de eventos, y estado general de salud.
 */
@Entity
@Table(name = "webhook_health_stats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookHealthStats {

    /**
     * Identificador único de las estadísticas
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Webhook asociado a estas estadísticas
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_config_id", nullable = false)
    private WebhookConfig webhookConfig;
    
    /**
     * Nombre del webhook para facilitar consultas
     */
    @Column(nullable = false)
    private String webhookName;
    
    /**
     * Número total de mensajes procesados
     */
    @Column(nullable = false)
    private long totalMessages;
    
    /**
     * Número de mensajes entregados exitosamente
     */
    @Column(nullable = false)
    private long deliveredMessages;
    
    /**
     * Número de mensajes fallidos permanentemente
     */
    @Column(nullable = false)
    private long failedMessages;
    
    /**
     * Número de mensajes actualmente pendientes o en reintentos
     */
    @Column(nullable = false)
    private long pendingMessages;
    
    /**
     * Número total de intentos de entrega realizados
     */
    @Column(nullable = false)
    private long totalAttempts;
    
    /**
     * Número de intentos exitosos
     */
    @Column(nullable = false)
    private long successfulAttempts;
    
    /**
     * Tiempo de respuesta promedio en milisegundos
     */
    @Column(nullable = false)
    private double averageResponseTime;
    
    /**
     * Último tiempo de respuesta registrado en milisegundos
     */
    @Column
    private Long lastResponseTime;
    
    /**
     * Tasa de éxito (porcentaje de intentos exitosos)
     */
    @Column(nullable = false)
    private double successRate;
    
    /**
     * Estado de salud actual
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthStatus healthStatus;
    
    /**
     * Última fecha en que se comprobó la salud
     */
    @Column(nullable = false)
    private OffsetDateTime lastHealthCheck;
    
    /**
     * Última fecha en que el estado cambió
     */
    @Column(nullable = false)
    private OffsetDateTime lastStatusChange;
    
    /**
     * Tiempo en funcionamiento (uptime) en porcentaje
     */
    @Column(nullable = false)
    private double uptime;
    
    /**
     * Número de veces que el estado ha cambiado en las últimas 24 horas
     */
    @Column(nullable = false)
    private int statusChangesLast24h;
    
    /**
     * Tiempo desde el último mensaje entregado con éxito
     */
    @Column
    private OffsetDateTime lastSuccessfulDelivery;
    
    /**
     * Tiempo desde el último error
     */
    @Column
    private OffsetDateTime lastFailure;
    
    /**
     * Código de error más frecuente
     */
    @Column
    private Integer mostCommonErrorCode;
    
    /**
     * Mensaje de error más frecuente
     */
    @Column(columnDefinition = "text")
    private String mostCommonErrorMessage;
    
    /**
     * Última actualización de estas estadísticas
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
    
    /**
     * Períodos de cálculo para estadísticas
     */
    @Column(nullable = false)
    private String statsTimeframe;
    
    /**
     * Posibles estados de salud para un webhook
     */
    public enum HealthStatus {
        /**
         * Funcionando correctamente con alta tasa de éxito
         */
        HEALTHY,
        
        /**
         * Funciona pero con algunos problemas o avisos
         */
        DEGRADED,
        
        /**
         * No funciona correctamente, fallos frecuentes
         */
        UNHEALTHY,
        
        /**
         * Sin datos suficientes para determinar estado
         */
        UNKNOWN,
        
        /**
         * Configuración inactiva o pausada
         */
        INACTIVE
    }
    
    /**
     * Actualiza las estadísticas con un nuevo intento de entrega
     * 
     * @param attempt Intento de entrega a considerar
     */
    public void updateWithDeliveryAttempt(DeliveryAttempt attempt) {
        // Incrementar contadores
        totalAttempts++;
        
        if (attempt.isSuccessful()) {
            successfulAttempts++;
            lastSuccessfulDelivery = attempt.getTimestamp();
        } else {
            lastFailure = attempt.getTimestamp();
            
            // Actualizar error más común si corresponde
            if (attempt.getStatusCode() != null) {
                if (mostCommonErrorCode == null) {
                    mostCommonErrorCode = attempt.getStatusCode();
                }
                // Una implementación real utilizaría un contador por código
            }
            
            if (attempt.getError() != null) {
                if (mostCommonErrorMessage == null) {
                    mostCommonErrorMessage = attempt.getError();
                }
                // Una implementación real utilizaría un contador por mensaje
            }
        }
        
        // Actualizar tiempo de respuesta promedio
        if (attempt.getRequestDuration() != null) {
            if (averageResponseTime == 0) {
                averageResponseTime = attempt.getRequestDuration();
            } else {
                // Media móvil ponderada (más peso a valores recientes)
                averageResponseTime = (averageResponseTime * 0.7) + (attempt.getRequestDuration() * 0.3);
            }
            
            lastResponseTime = attempt.getRequestDuration();
        }
        
        // Recalcular tasa de éxito
        successRate = (double) successfulAttempts / totalAttempts * 100;
        
        // Actualizar estado de salud
        updateHealthStatus();
    }
    
    /**
     * Actualiza las estadísticas con un nuevo mensaje
     * 
     * @param message Mensaje a considerar
     */
    public void updateWithMessageStatus(Message message) {
        // Actualizar contadores según estado del mensaje
        boolean needStatusUpdate = false;
        
        switch (message.getStatus()) {
            case DELIVERED:
                deliveredMessages++;
                needStatusUpdate = true;
                break;
            case FAILED:
                if (!message.isRetryable()) {
                    failedMessages++;
                    needStatusUpdate = true;
                }
                break;
            case PENDING:
            case PROCESSING:
                pendingMessages++;
                break;
            case CANCELLED:
                // No afecta métricas de salud
                break;
        }
        
        // Solo actualizar estado si cambió a un estado terminal
        if (needStatusUpdate) {
            updateHealthStatus();
        }
        
        // Actualizar contador total
        totalMessages = deliveredMessages + failedMessages + pendingMessages;
    }
    
    /**
     * Calcula y actualiza el estado de salud basado en métricas actuales
     */
    private void updateHealthStatus() {
        HealthStatus oldStatus = this.healthStatus;
        
        // Determinar nuevo estado basado en métricas
        if (totalAttempts < 5) {
            // Muy pocos datos para determinar
            this.healthStatus = HealthStatus.UNKNOWN;
        } else if (successRate >= 95) {
            this.healthStatus = HealthStatus.HEALTHY;
        } else if (successRate >= 75) {
            this.healthStatus = HealthStatus.DEGRADED;
        } else {
            this.healthStatus = HealthStatus.UNHEALTHY;
        }
        
        // Marcar inactivo si corresponde
        if (webhookConfig != null && !webhookConfig.isActive()) {
            this.healthStatus = HealthStatus.INACTIVE;
        }
        
        // Actualizar timestamp de cambio si cambió el estado
        if (oldStatus != this.healthStatus) {
            this.lastStatusChange = OffsetDateTime.now();
            this.statusChangesLast24h++;
        }
        
        // Actualizar última verificación de salud
        this.lastHealthCheck = OffsetDateTime.now();
    }
    
    /**
     * Realiza una comprobación completa de salud incluyendo
     * análisis del historial reciente
     */
    public void performHealthCheck() {
        // Aquí iría lógica más avanzada que considere:
        // - Tendencias en tiempos de respuesta
        // - Patrones recurrentes de fallos
        // - Análisis de códigos de error específicos
        // - Verificación de conectividad proactiva
        
        updateHealthStatus();
        
        // Calcular uptime basado en estado y tiempo
        if (this.healthStatus == HealthStatus.HEALTHY) {
            // Incrementar ligeramente el uptime
            this.uptime = Math.min(100, this.uptime + 0.1);
        } else if (this.healthStatus == HealthStatus.UNHEALTHY) {
            // Reducir significativamente el uptime
            this.uptime = Math.max(0, this.uptime - 1.0);
        }
        
        // Limpiar contador de cambios de estado si han pasado 24h
        if (this.lastStatusChange.plusHours(24).isBefore(OffsetDateTime.now())) {
            this.statusChangesLast24h = 0;
        }
    }
    
    /**
     * Crea y asocia estadísticas iniciales para una configuración de webhook
     * 
     * @param config Configuración de webhook
     * @return Nuevas estadísticas de salud
     */
    public static WebhookHealthStats createInitialStats(WebhookConfig config) {
        WebhookHealthStats stats = new WebhookHealthStats();
        stats.setWebhookConfig(config);
        stats.setWebhookName(config.getName());
        stats.setTotalMessages(0);
        stats.setDeliveredMessages(0);
        stats.setFailedMessages(0);
        stats.setPendingMessages(0);
        stats.setTotalAttempts(0);
        stats.setSuccessfulAttempts(0);
        stats.setAverageResponseTime(0);
        stats.setSuccessRate(100); // Optimista al inicio
        stats.setHealthStatus(HealthStatus.UNKNOWN);
        stats.setLastHealthCheck(OffsetDateTime.now());
        stats.setLastStatusChange(OffsetDateTime.now());
        stats.setUptime(100); // Optimista al inicio
        stats.setStatusChangesLast24h(0);
        stats.setStatsTimeframe("ALL");
        
        return stats;
    }
    
    /**
     * Obtiene una representación resumida del estado de salud
     * 
     * @return String con resumen de salud
     */
    @Transient
    public String getHealthSummary() {
        return String.format("%s (%.1f%% éxito, %.0f ms)",
                healthStatus.name(),
                successRate,
                averageResponseTime);
    }
    
    /**
     * Verifica si el webhook está en estado problemático que requiere atención
     * 
     * @return true si necesita atención
     */
    @Transient
    public boolean needsAttention() {
        return healthStatus == HealthStatus.UNHEALTHY || 
               (healthStatus == HealthStatus.DEGRADED && statusChangesLast24h > 3);
    }
}