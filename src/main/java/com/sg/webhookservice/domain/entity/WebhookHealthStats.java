package com.sg.webhookservice.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad que almacena estadísticas de salud para un webhook.
 * Mantiene métricas como tasas de éxito, tiempos de respuesta,
 * y estados de entregas.
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
     * ID de la configuración de webhook (para facilitar queries)
     */
    @Column(nullable = false)
    private UUID webhookConfigId;

    /**
     * Nombre del webhook para facilitar consultas
     */
    @Column(nullable = false)
    private String webhookName;

    /**
     * Número total de mensajes enviados
     */
    @Column(nullable = false)
    private int totalSent;

    /**
     * Número de mensajes entregados exitosamente
     */
    @Column(nullable = false)
    private int totalDelivered;

    /**
     * Número de mensajes fallidos
     */
    @Column(nullable = false)
    private int totalFailed;

    /**
     * Tiempo de respuesta promedio en milisegundos
     */
    @Column(nullable = false)
    private double avgResponseTime;

    /**
     * Último momento en que se entregó un mensaje exitosamente
     */
    @Column
    private OffsetDateTime lastSuccessTime;

    /**
     * Último momento en que falló un mensaje
     */
    @Column
    private OffsetDateTime lastErrorTime;

    /**
     * Último mensaje de error
     */
    @Column(columnDefinition = "text")
    private String lastError;

    /**
     * Fecha de creación de las estadísticas
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Fecha de última actualización de las estadísticas
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Registra una entrega exitosa
     *
     * @param responseTimeMs Tiempo de respuesta en milisegundos
     */
    public void recordSuccessfulDelivery(long responseTimeMs) {
        totalSent++;
        totalDelivered++;
        lastSuccessTime = OffsetDateTime.now();

        // Actualizar promedio de tiempo de respuesta (promedio móvil ponderado)
        if (avgResponseTime == 0) {
            avgResponseTime = responseTimeMs;
        } else {
            avgResponseTime = (avgResponseTime * 0.7) + (responseTimeMs * 0.3);
        }
    }

    /**
     * Registra una entrega fallida
     *
     * @param errorMessage Mensaje de error
     */
    public void recordFailedDelivery(String errorMessage) {
        totalSent++;
        totalFailed++;
        lastErrorTime = OffsetDateTime.now();
        lastError = errorMessage;
    }

    /**
     * Calcula la tasa de éxito
     *
     * @return Porcentaje de entregas exitosas
     */
    @Transient
    public double getSuccessRate() {
        if (totalSent == 0) {
            return 100.0; // No hay datos suficientes
        }

        return (double) totalDelivered / totalSent * 100;
    }

    /**
     * Determina el estado de salud del webhook basado en la tasa de éxito
     *
     * @return Estado de salud como string
     */
    @Transient
    public String getHealthStatus() {
        double successRate = getSuccessRate();

        if (totalSent < 5) {
            return "UNKNOWN"; // No hay datos suficientes
        }

        if (successRate >= 95) {
            return "HEALTHY";
        } else if (successRate >= 75) {
            return "DEGRADED";
        } else {
            return "UNHEALTHY";
        }
    }

    /**
     * Crea una instancia inicial de estadísticas para un webhook
     *
     * @param webhookConfig Configuración del webhook
     * @return Nueva instancia de WebhookHealthStats
     */
    public static WebhookHealthStats createInitial(WebhookConfig webhookConfig) {
        WebhookHealthStats stats = new WebhookHealthStats();
        stats.setWebhookConfig(webhookConfig);
        stats.setWebhookConfigId(webhookConfig.getId());
        stats.setWebhookName(webhookConfig.getName());
        stats.setTotalSent(0);
        stats.setTotalDelivered(0);
        stats.setTotalFailed(0);
        stats.setAvgResponseTime(0);
        return stats;
    }
}
