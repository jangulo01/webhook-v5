package com.sg.webhookservice.dto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DTO para las respuestas del endpoint de verificación de salud del servicio
 */
public class HealthCheckResponse {

    /**
     * Estado del servicio (healthy, degraded, unhealthy)
     */
    private String status;

    /**
     * Versión del servicio
     */
    private String version;

    /**
     * Información sobre la conexión a la base de datos
     */
    private String database;

    /**
     * Esquema de la base de datos en uso
     */
    private String schema;

    /**
     * Estado de Kafka (url o 'disabled' si está en modo directo)
     */
    private String kafka;

    /**
     * Modo de operación (direct o kafka)
     */
    private String mode;

    /**
     * URL de destino sobreescrita (si existe)
     */
    private String destinationUrlOverride;

    /**
     * Disponibilidad del módulo HMAC (available o not available)
     */
    private String hmacModule;

    /**
     * Timestamp de la última verificación
     */
    private LocalDateTime timestamp;

    /**
     * Métricas adicionales del sistema
     */
    private Map<String, Object> metrics;

    /**
     * Constructor por defecto
     */
    public HealthCheckResponse() {
        this.timestamp = LocalDateTime.now();
        this.metrics = new HashMap<>();
    }

    /**
     * Constructor con los campos principales
     *
     * @param status Estado del servicio
     * @param version Versión del servicio
     * @param database Información de la base de datos
     * @param schema Esquema de la base de datos
     * @param kafka Estado de Kafka
     * @param mode Modo de operación
     */
    public HealthCheckResponse(String status, String version, String database,
                               String schema, String kafka, String mode) {
        this();
        this.status = status;
        this.version = version;
        this.database = database;
        this.schema = schema;
        this.kafka = kafka;
        this.mode = mode;
    }

    /**
     * Constructor completo
     *
     * @param status Estado del servicio
     * @param version Versión del servicio
     * @param database Información de la base de datos
     * @param schema Esquema de la base de datos
     * @param kafka Estado de Kafka
     * @param mode Modo de operación
     * @param destinationUrlOverride URL de destino sobreescrita
     * @param hmacModule Disponibilidad del módulo HMAC
     */
    public HealthCheckResponse(String status, String version, String database,
                               String schema, String kafka, String mode,
                               String destinationUrlOverride, String hmacModule) {
        this(status, version, database, schema, kafka, mode);
        this.destinationUrlOverride = destinationUrlOverride;
        this.hmacModule = hmacModule;
    }

    /**
     * Obtiene el estado del servicio
     *
     * @return Estado del servicio
     */
    public String getStatus() {
        return status;
    }

    /**
     * Establece el estado del servicio
     *
     * @param status Estado del servicio
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Obtiene la versión del servicio
     *
     * @return Versión del servicio
     */
    public String getVersion() {
        return version;
    }

    /**
     * Establece la versión del servicio
     *
     * @param version Versión del servicio
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Obtiene la información de la base de datos
     *
     * @return Información de la base de datos
     */
    public String getDatabase() {
        return database;
    }

    /**
     * Establece la información de la base de datos
     *
     * @param database Información de la base de datos
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    /**
     * Obtiene el esquema de la base de datos
     *
     * @return Esquema de la base de datos
     */
    public String getSchema() {
        return schema;
    }

    /**
     * Establece el esquema de la base de datos
     *
     * @param schema Esquema de la base de datos
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    /**
     * Obtiene el estado de Kafka
     *
     * @return Estado de Kafka
     */
    public String getKafka() {
        return kafka;
    }

    /**
     * Establece el estado de Kafka
     *
     * @param kafka Estado de Kafka
     */
    public void setKafka(String kafka) {
        this.kafka = kafka;
    }

    /**
     * Obtiene el modo de operación
     *
     * @return Modo de operación
     */
    public String getMode() {
        return mode;
    }

    /**
     * Establece el modo de operación
     *
     * @param mode Modo de operación
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * Obtiene la URL de destino sobreescrita
     *
     * @return URL de destino sobreescrita
     */
    public String getDestinationUrlOverride() {
        return destinationUrlOverride;
    }

    /**
     * Establece la URL de destino sobreescrita
     *
     * @param destinationUrlOverride URL de destino sobreescrita
     */
    public void setDestinationUrlOverride(String destinationUrlOverride) {
        this.destinationUrlOverride = destinationUrlOverride;
    }

    /**
     * Obtiene la disponibilidad del módulo HMAC
     *
     * @return Disponibilidad del módulo HMAC
     */
    public String getHmacModule() {
        return hmacModule;
    }

    /**
     * Establece la disponibilidad del módulo HMAC
     *
     * @param hmacModule Disponibilidad del módulo HMAC
     */
    public void setHmacModule(String hmacModule) {
        this.hmacModule = hmacModule;
    }

    /**
     * Obtiene el timestamp de la última verificación
     *
     * @return Timestamp de la última verificación
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Establece el timestamp de la última verificación
     *
     * @param timestamp Timestamp de la última verificación
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Obtiene las métricas adicionales del sistema
     *
     * @return Métricas adicionales del sistema
     */
    public Map<String, Object> getMetrics() {
        return metrics;
    }

    /**
     * Establece las métricas adicionales del sistema
     *
     * @param metrics Métricas adicionales del sistema
     */
    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }

    /**
     * Agrega una métrica al mapa de métricas
     *
     * @param key Clave de la métrica
     * @param value Valor de la métrica
     */
    public void addMetric(String key, Object value) {
        this.metrics.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HealthCheckResponse that = (HealthCheckResponse) o;
        return Objects.equals(status, that.status) &&
                Objects.equals(version, that.version) &&
                Objects.equals(database, that.database) &&
                Objects.equals(schema, that.schema) &&
                Objects.equals(kafka, that.kafka) &&
                Objects.equals(mode, that.mode) &&
                Objects.equals(destinationUrlOverride, that.destinationUrlOverride) &&
                Objects.equals(hmacModule, that.hmacModule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, version, database, schema, kafka, mode,
                destinationUrlOverride, hmacModule);
    }

    @Override
    public String toString() {
        return "HealthCheckResponse{" +
                "status='" + status + '\'' +
                ", version='" + version + '\'' +
                ", database='" + database + '\'' +
                ", schema='" + schema + '\'' +
                ", kafka='" + kafka + '\'' +
                ", mode='" + mode + '\'' +
                ", destinationUrlOverride='" + destinationUrlOverride + '\'' +
                ", hmacModule='" + hmacModule + '\'' +
                ", timestamp=" + timestamp +
                ", metrics=" + metrics +
                '}';
    }

    /**
     * Crea una respuesta de estado saludable
     *
     * @param version Versión del servicio
     * @param database Información de la base de datos
     * @param schema Esquema de la base de datos
     * @param kafka Estado de Kafka
     * @param mode Modo de operación
     * @param destinationUrlOverride URL de destino sobreescrita
     * @param hmacModule Disponibilidad del módulo HMAC
     * @return Una nueva instancia de HealthCheckResponse con estado 'healthy'
     */
    public static HealthCheckResponse healthy(String version, String database,
                                              String schema, String kafka, String mode,
                                              String destinationUrlOverride, String hmacModule) {
        return new HealthCheckResponse(
                "healthy", version, database, schema,
                kafka, mode, destinationUrlOverride, hmacModule
        );
    }

    /**
     * Crea una respuesta de estado degradado
     *
     * @param version Versión del servicio
     * @param database Información de la base de datos
     * @param schema Esquema de la base de datos
     * @param kafka Estado de Kafka
     * @param mode Modo de operación
     * @param destinationUrlOverride URL de destino sobreescrita
     * @param hmacModule Disponibilidad del módulo HMAC
     * @param reason Razón del estado degradado
     * @return Una nueva instancia de HealthCheckResponse con estado 'degraded'
     */
    public static HealthCheckResponse degraded(String version, String database,
                                               String schema, String kafka, String mode,
                                               String destinationUrlOverride, String hmacModule,
                                               String reason) {
        HealthCheckResponse response = new HealthCheckResponse(
                "degraded", version, database, schema,
                kafka, mode, destinationUrlOverride, hmacModule
        );
        response.addMetric("degradationReason", reason);
        return response;
    }

    /**
     * Crea una respuesta de estado no saludable
     *
     * @param version Versión del servicio
     * @param error Descripción del error
     * @return Una nueva instancia de HealthCheckResponse con estado 'unhealthy'
     */
    public static HealthCheckResponse unhealthy(String version, String error) {
        HealthCheckResponse response = new HealthCheckResponse();
        response.setStatus("unhealthy");
        response.setVersion(version);
        response.addMetric("error", error);
        return response;
    }
}