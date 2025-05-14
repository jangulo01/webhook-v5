package com.sg.webhookservice.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidad principal que representa un mensaje de webhook.
 *
 * Contiene toda la información necesaria para entregar un webhook,
 * incluyendo payload, destino, estado actual, historial de intentos, etc.
 */
@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /**
     * Identificador único del mensaje
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Configuración de webhook asociada a este mensaje
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_config_id", nullable = false)
    private WebhookConfig webhookConfig;

    /**
     * Payload del webhook (contenido a enviar)
     */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    /**
     * URL de destino para este mensaje específico
     * (puede ser diferente a la URL en la configuración si se especificó un override)
     */
    @Column(nullable = false)
    private String targetUrl;

    /**
     * Estado actual del mensaje
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status;

    /**
     * Número de reintentos realizados hasta ahora
     */
    @Column(nullable = false)
    private int retryCount;

    /**
     * Tiempo programado para el próximo reintento
     */
    @Column
    private OffsetDateTime nextRetry;

    /**
     * Firma HMAC calculada para este mensaje
     */
    @Column
    private String signature;

    /**
     * Cabeceras HTTP adicionales a incluir en la solicitud (almacenadas como JSON)
     */
    @Column(columnDefinition = "jsonb")
    private String headers;

    /**
     * Último mensaje de error encontrado
     */
    @Column(columnDefinition = "text")
    private String lastError;

    /**
     * Momento de creación del mensaje
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Momento de última actualización del mensaje
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Historial de intentos de entrega para este mensaje
     */
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attemptNumber ASC")
    private List<DeliveryAttempt> deliveryAttempts = new ArrayList<>();

    /**
     * Estados posibles para un mensaje de webhook
     */
    public enum MessageStatus {
        /**
         * Recién creado, pendiente de procesamiento
         */
        PENDING,

        /**
         * Actualmente en proceso de envío
         */
        PROCESSING,

        /**
         * Entregado exitosamente al destino
         */
        DELIVERED,

        /**
         * Fallido después de todos los reintentos o por error permanente
         */
        FAILED,

        /**
         * Cancelado manualmente o por política de sistema
         */
        CANCELLED
    }

    /**
     * Agrega un nuevo intento de entrega al historial
     *
     * @param attempt Intento de entrega a agregar
     */
    public void addDeliveryAttempt(DeliveryAttempt attempt) {
        deliveryAttempts.add(attempt);
        attempt.setMessage(this);
        attempt.setAttemptNumber(this.retryCount + 1);
        this.retryCount = attempt.getAttemptNumber();
    }

    /**
     * Verifica si este mensaje está en un estado terminal
     * (no se procesará más)
     *
     * @return true si está en estado terminal
     */
    @Transient
    public boolean isInTerminalState() {
        return status == MessageStatus.DELIVERED ||
                status == MessageStatus.CANCELLED ||
                (status == MessageStatus.FAILED && nextRetry == null);
    }

    /**
     * Verifica si este mensaje puede ser reintentado
     *
     * @return true si puede reintentarse
     */
    @Transient
    public boolean isRetryable() {
        return status == MessageStatus.FAILED && nextRetry != null;
    }

    /**
     * Verifica si es tiempo de reintentar este mensaje
     * basándose en nextRetry
     *
     * @return true si es tiempo de reintentar
     */
    @Transient
    public boolean isReadyForRetry() {
        return isRetryable() &&
                nextRetry != null &&
                nextRetry.isBefore(OffsetDateTime.now());
    }

    /**
     * Obtiene el último intento de entrega
     *
     * @return Último intento o null si no hay intentos
     */
    @Transient
    public DeliveryAttempt getLastAttempt() {
        if (deliveryAttempts == null || deliveryAttempts.isEmpty()) {
            return null;
        }

        return deliveryAttempts.get(deliveryAttempts.size() - 1);
    }

    /**
     * Calcula la tasa de éxito de entregas para este mensaje
     *
     * @return Porcentaje de éxito (0-100) o null si no hay intentos
     */
    @Transient
    public Double getSuccessRate() {
        if (deliveryAttempts == null || deliveryAttempts.isEmpty()) {
            return null;
        }

        long successCount = deliveryAttempts.stream()
                .filter(DeliveryAttempt::isSuccessful)
                .count();

        return (double) successCount / deliveryAttempts.size() * 100;
    }

    /**
     * Verifica si el mensaje ha excedido el máximo de reintentos permitidos
     *
     * @return true si ha excedido el máximo de reintentos
     */
    @Transient
    public boolean hasExceededMaxRetries() {
        if (webhookConfig == null) {
            return retryCount >= 3; // valor por defecto
        }

        return retryCount >= webhookConfig.getMaxRetries();
    }

    /**
     * Marca el mensaje como entregado exitosamente
     */
    public void markAsDelivered() {
        this.status = MessageStatus.DELIVERED;
        this.nextRetry = null;
    }

    /**
     * Marca el mensaje como fallido y programa un reintento si corresponde
     *
     * @param error Mensaje de error
     * @param nextRetryTime Tiempo para próximo reintento o null si no hay más reintentos
     */
    public void markAsFailed(String error, OffsetDateTime nextRetryTime) {
        this.status = MessageStatus.FAILED;
        this.lastError = error;
        this.nextRetry = nextRetryTime;
    }

    /**
     * Marca el mensaje como en procesamiento
     */
    public void markAsProcessing() {
        this.status = MessageStatus.PROCESSING;
    }

    /**
     * Cancela el mensaje (no se procesará más)
     */
    public void cancel() {
        this.status = MessageStatus.CANCELLED;
        this.nextRetry = null;
    }
}