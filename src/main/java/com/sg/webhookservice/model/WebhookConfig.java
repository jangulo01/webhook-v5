package com.sg.webhookservice.model;

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
 * Entidad que representa una configuración de webhook.
 * 
 * Define los parámetros para un tipo específico de webhook, incluyendo:
 * - Destino y autenticación
 * - Estrategias de reintento
 * - Políticas de expiración
 * - Cabeceras personalizadas
 */
@Entity
@Table(name = "webhook_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookConfig {

    /**
     * Identificador único de la configuración
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Nombre único de este webhook
     */
    @Column(unique = true, nullable = false, length = 100)
    private String name;
    
    /**
     * URL de destino para enviar los webhooks
     */
    @Column(nullable = false)
    private String targetUrl;
    
    /**
     * Secreto utilizado para firmar los webhooks
     */
    @Column(nullable = false)
    private String secret;
    
    /**
     * Número máximo de reintentos para mensajes fallidos
     */
    @Column(nullable = false)
    private int maxRetries;
    
    /**
     * Estrategia de backoff para reintentos
     */
    @Column(nullable = false)
    private String backoffStrategy;
    
    /**
     * Intervalo inicial entre reintentos (en segundos)
     */
    @Column(nullable = false)
    private int initialInterval;
    
    /**
     * Factor de multiplicación para backoff exponencial
     */
    @Column(nullable = false)
    private double backoffFactor;
    
    /**
     * Intervalo máximo entre reintentos (en segundos)
     */
    @Column(nullable = false)
    private int maxInterval;
    
    /**
     * Tiempo máximo de vida para mensajes (en segundos)
     */
    @Column(nullable = false)
    private int maxAge;
    
    /**
     * Cabeceras HTTP personalizadas a incluir en las solicitudes (almacenadas como JSON)
     */
    @Column(columnDefinition = "jsonb")
    private String headers;
    
    /**
     * Estado de activación del webhook
     */
    @Column(nullable = false)
    private boolean active;
    
    /**
     * Momento de creación de la configuración
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    
    /**
     * Momento de última actualización de la configuración
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;
    
    /**
     * Descripciones para facilitar administración
     */
    @Column(columnDefinition = "text")
    private String description;
    
    /**
     * Etiquetas para categorización y filtrado
     */
    @Column(columnDefinition = "jsonb")
    private String tags;
    
    /**
     * Grupo o categoría al que pertenece este webhook
     */
    @Column
    private String group;
    
    /**
     * Prioridad de procesamiento (valores más altos = mayor prioridad)
     */
    @Column
    private Integer priority;
    
    /**
     * Referencias a los mensajes asociados a esta configuración
     */
    @OneToMany(mappedBy = "webhookConfig", cascade = CascadeType.ALL)
    private List<Message> messages = new ArrayList<>();
    
    /**
     * Estadísticas de salud asociadas a esta configuración
     */
    @OneToOne(mappedBy = "webhookConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    private WebhookHealthStats healthStats;
    
    /**
     * Establece valores predeterminados para campos opcionales
     */
    @PrePersist
    public void setDefaultValues() {
        if (maxRetries <= 0) {
            maxRetries = 3;
        }
        
        if (backoffStrategy == null || backoffStrategy.isEmpty()) {
            backoffStrategy = "exponential";
        }
        
        if (initialInterval <= 0) {
            initialInterval = 60; // 60 segundos
        }
        
        if (backoffFactor <= 0) {
            backoffFactor = 2.0;
        }
        
        if (maxInterval <= 0) {
            maxInterval = 3600; // 1 hora
        }
        
        if (maxAge <= 0) {
            maxAge = 86400; // 1 día
        }
        
        if (priority == null) {
            priority = 0; // prioridad normal
        }
        
        active = true; // activo por defecto
    }
    
    /**
     * Calcula el tiempo de espera para un reintento específico
     * según la estrategia de backoff configurada
     * 
     * @param retryCount Número de reintento actual
     * @return Tiempo de espera en segundos
     */
    @Transient
    public int calculateBackoffDelay(int retryCount) {
        switch (backoffStrategy.toLowerCase()) {
            case "linear":
                return Math.min(initialInterval * (1 + retryCount), maxInterval);
                
            case "exponential":
                return (int) Math.min(initialInterval * Math.pow(backoffFactor, retryCount), maxInterval);
                
            case "fixed":
                return initialInterval;
                
            default:
                // Si no reconocemos la estrategia, usar exponencial por defecto
                return (int) Math.min(initialInterval * Math.pow(2.0, retryCount), maxInterval);
        }
    }
    
    /**
     * Verifica si se ha excedido el tiempo máximo de vida
     * para un mensaje creado en el timestamp dado
     * 
     * @param messageTimestamp Momento de creación del mensaje
     * @return true si el mensaje debe considerarse expirado
     */
    @Transient
    public boolean isMessageExpired(OffsetDateTime messageTimestamp) {
        if (messageTimestamp == null) {
            return false;
        }
        
        OffsetDateTime expirationTime = messageTimestamp.plusSeconds(maxAge);
        return expirationTime.isBefore(OffsetDateTime.now());
    }
    
    /**
     * Crea una copia de esta configuración con un nuevo nombre
     * 
     * @param newName Nuevo nombre para la copia
     * @return Nueva instancia de WebhookConfig
     */
    @Transient
    public WebhookConfig duplicate(String newName) {
        WebhookConfig copy = new WebhookConfig();
        copy.setName(newName);
        copy.setTargetUrl(this.targetUrl);
        copy.setSecret(this.secret);
        copy.setMaxRetries(this.maxRetries);
        copy.setBackoffStrategy(this.backoffStrategy);
        copy.setInitialInterval(this.initialInterval);
        copy.setBackoffFactor(this.backoffFactor);
        copy.setMaxInterval(this.maxInterval);
        copy.setMaxAge(this.maxAge);
        copy.setHeaders(this.headers);
        copy.setDescription("Duplicado de " + this.name);
        copy.setTags(this.tags);
        copy.setGroup(this.group);
        copy.setPriority(this.priority);
        copy.setActive(this.active);
        
        return copy;
    }
    
    /**
     * Verifica si este webhook tiene una estrategia de backoff exponencial
     * 
     * @return true si la estrategia es exponencial
     */
    @Transient
    public boolean isExponentialBackoff() {
        return "exponential".equalsIgnoreCase(backoffStrategy);
    }
    
    /**
     * Verifica si este webhook tiene una estrategia de backoff lineal
     * 
     * @return true si la estrategia es lineal
     */
    @Transient
    public boolean isLinearBackoff() {
        return "linear".equalsIgnoreCase(backoffStrategy);
    }
    
    /**
     * Verifica si este webhook tiene una estrategia de backoff fija
     * 
     * @return true si la estrategia es fija
     */
    @Transient
    public boolean isFixedBackoff() {
        return "fixed".equalsIgnoreCase(backoffStrategy);
    }
    
    /**
     * Obtiene el máximo tiempo total de reintentos posible
     * (suma de todos los intervalos posibles)
     * 
     * @return Tiempo total máximo en segundos
     */
    @Transient
    public int getMaximumTotalRetryTime() {
        int total = 0;
        
        for (int i = 0; i < maxRetries; i++) {
            total += calculateBackoffDelay(i);
        }
        
        return total;
    }
}