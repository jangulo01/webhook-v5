package com.sg.webhookservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entidad que representa un intento de entrega de un mensaje de webhook.
 * 
 * Cada vez que se intenta entregar un mensaje a su destino, se registra
 * un DeliveryAttempt con los detalles del intento, incluyendo la respuesta
 * obtenida o el error encontrado.
 */
@Entity
@Table(name = "delivery_attempts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAttempt {

    /**
     * Identificador único del intento de entrega
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Referencia al mensaje relacionado con este intento
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;
    
    /**
     * Número secuencial del intento (1 para el primer intento, 2 para el primer reintento, etc.)
     */
    @Column(nullable = false)
    private int attemptNumber;
    
    /**
     * Momento en que se realizó el intento
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime timestamp;
    
    /**
     * Código de estado HTTP recibido del servidor destino (null si hubo error de conexión)
     */
    @Column
    private Integer statusCode;
    
    /**
     * Cuerpo de la respuesta recibida del servidor destino (truncado si es muy largo)
     */
    @Column(columnDefinition = "text")
    private String responseBody;
    
    /**
     * Mensaje de error en caso de fallo (exception message)
     */
    @Column(columnDefinition = "text")
    private String error;
    
    /**
     * Duración de la solicitud en milisegundos
     */
    @Column
    private Long requestDuration;
    
    /**
     * URL a la que se envió la solicitud (puede ser diferente a la configurada
     * si se usó una URL de override)
     */
    @Column
    private String targetUrl;
    
    /**
     * Cabeceras recibidas en la respuesta (almacenadas como JSON)
     */
    @Column(columnDefinition = "jsonb")
    private String responseHeaders;
    
    /**
     * Nodo o instancia del servicio que procesó este intento
     */
    @Column
    private String processingNode;
    
    /**
     * Verifica si este intento fue exitoso
     * 
     * @return true si el intento fue exitoso (código 2xx)
     */
    @Transient
    public boolean isSuccessful() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
    
    /**
     * Verifica si este intento resultó en un error del lado del cliente (4xx)
     * 
     * @return true si fue un error del cliente
     */
    @Transient
    public boolean isClientError() {
        return statusCode != null && statusCode >= 400 && statusCode < 500;
    }
    
    /**
     * Verifica si este intento resultó en un error del lado del servidor (5xx)
     * 
     * @return true si fue un error del servidor
     */
    @Transient
    public boolean isServerError() {
        return statusCode != null && statusCode >= 500 && statusCode < 600;
    }
    
    /**
     * Verifica si este intento resultó en un error de conexión
     * 
     * @return true si hubo un error de conexión
     */
    @Transient
    public boolean isConnectionError() {
        return statusCode == null && error != null;
    }
    
    /**
     * Obtiene un resumen del resultado de este intento
     * 
     * @return String describiendo el resultado
     */
    @Transient
    public String getResultSummary() {
        if (isSuccessful()) {
            return String.format("Éxito (HTTP %d) en %d ms", statusCode, requestDuration);
        } else if (isClientError()) {
            return String.format("Error de cliente (HTTP %d) en %d ms", statusCode, requestDuration);
        } else if (isServerError()) {
            return String.format("Error de servidor (HTTP %d) en %d ms", statusCode, requestDuration);
        } else if (isConnectionError()) {
            return String.format("Error de conexión: %s", 
                    error != null ? error.substring(0, Math.min(error.length(), 100)) : "desconocido");
        } else {
            return "Resultado desconocido";
        }
    }
    
    /**
     * Determina si un reintento es apropiado basado en el resultado de este intento
     * 
     * @return true si debería reintentarse
     */
    @Transient
    public boolean shouldRetry() {
        // Reintentar en caso de errores de servidor o errores de conexión
        return isServerError() || isConnectionError() || 
               // También reintentar para algunos códigos de error específicos
               statusCode == 429 || // Too Many Requests
               statusCode == 408;    // Request Timeout
    }
    
    /**
     * Calcula un factor de retraso basado en el resultado
     * para ajustar dinámicamente la estrategia de backoff
     * 
     * @return factor a aplicar al intervalo de reintento
     */
    @Transient
    public double getRetryDelayFactor() {
        if (statusCode == 429) {
            // Rate limiting - esperar más tiempo
            return 2.0;
        } else if (isServerError()) {
            // Errores de servidor - espera estándar
            return 1.0;
        } else if (isConnectionError()) {
            // Errores de conexión - incrementar ligeramente
            return 1.2;
        } else {
            // Otros casos
            return 1.0;
        }
    }
    
    /**
     * Trunca el texto de respuesta si excede un límite
     * 
     * @param response Texto completo de respuesta
     * @param maxLength Longitud máxima
     * @return Texto truncado si es necesario
     */
    public static String truncateResponse(String response, int maxLength) {
        if (response == null) {
            return null;
        }
        
        if (response.length() <= maxLength) {
            return response;
        }
        
        return response.substring(0, maxLength) + "...";
    }
}