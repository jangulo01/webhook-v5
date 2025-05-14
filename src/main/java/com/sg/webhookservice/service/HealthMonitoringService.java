package com.sg.webhookservice.service;

import com.sg.webhookservice.dto.HealthCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio para monitorear la salud del sistema y recopilar métricas
 */
@Service
public class HealthMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(HealthMonitoringService.class);

    private final JdbcTemplate jdbcTemplate;
    private final HmacService hmacService;
    private final AtomicReference<String> serviceStatus = new AtomicReference<>("starting");
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Value("${spring.application.version:1.0.0}")
    private String appVersion;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.schema:webhook_db}")
    private String dbSchema;

    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String kafkaServers;

    @Value("${webhook.service.direct-mode:false}")
    private boolean directMode;

    @Value("${webhook.service.destination-url-override:#{null}}")
    private String destinationUrlOverride;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Constructor del servicio
     *
     * @param dataSource DataSource para comprobar la conexión a la base de datos
     * @param hmacService Servicio HMAC para comprobar su disponibilidad
     */
    @Autowired
    public HealthMonitoringService(DataSource dataSource, HmacService hmacService) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.hmacService = hmacService;

        // Inicializar contadores de métricas
        initializeCounters();

        // Iniciar en estado saludable
        serviceStatus.set("healthy");
    }

    /**
     * Inicializa los contadores para las métricas
     */
    private void initializeCounters() {
        // Contadores de mensajes
        counters.put("messages.received", new AtomicLong(0));
        counters.put("messages.delivered", new AtomicLong(0));
        counters.put("messages.failed", new AtomicLong(0));
        counters.put("messages.retried", new AtomicLong(0));
        counters.put("messages.failed_attempts", new AtomicLong(0));
        counters.put("messages.connection_errors", new AtomicLong(0));

        // Contadores de operaciones
        counters.put("operations.webhook.created", new AtomicLong(0));
        counters.put("operations.webhook.updated", new AtomicLong(0));
        counters.put("operations.direct.send", new AtomicLong(0));
        counters.put("operations.bulk.retry", new AtomicLong(0));

        // Contadores de rendimiento
        counters.put("performance.avg_response_time_ms", new AtomicLong(0));
        counters.put("performance.max_response_time_ms", new AtomicLong(0));
        counters.put("performance.avg_failed_time_ms", new AtomicLong(0));
    }

    /**
     * Incrementa un contador específico
     *
     * @param counterName Nombre del contador a incrementar
     */
    public void incrementCounter(String counterName) {
        counters.computeIfAbsent(counterName, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Incrementa un contador específico en una cantidad determinada
     *
     * @param counterName Nombre del contador a incrementar
     * @param value Valor a incrementar
     */
    public void incrementCounter(String counterName, long value) {
        counters.computeIfAbsent(counterName, k -> new AtomicLong(0)).addAndGet(value);
    }

    /**
     * Establece un valor para un contador específico
     *
     * @param counterName Nombre del contador
     * @param value Valor a establecer
     */
    public void setCounter(String counterName, long value) {
        counters.computeIfAbsent(counterName, k -> new AtomicLong(0)).set(value);
    }

    /**
     * Obtiene el valor actual de un contador
     *
     * @param counterName Nombre del contador
     * @return Valor actual del contador
     */
    public long getCounter(String counterName) {
        return counters.getOrDefault(counterName, new AtomicLong(0)).get();
    }

    /**
     * Obtiene todos los contadores como un mapa
     *
     * @return Mapa con todos los contadores
     */
    public Map<String, Long> getAllCounters() {
        Map<String, Long> result = new HashMap<>();
        counters.forEach((key, value) -> result.put(key, value.get()));
        return result;
    }

    /**
     * Establece el estado del servicio
     *
     * @param status Nuevo estado del servicio
     */
    public void setServiceStatus(String status) {
        serviceStatus.set(status);
        logger.info("Service status changed to: {}", status);
    }

    /**
     * Obtiene el estado actual del servicio
     *
     * @return Estado actual del servicio
     */
    public String getServiceStatus() {
        return serviceStatus.get();
    }

    /**
     * Comprueba si la base de datos está disponible
     *
     * @return true si la base de datos está disponible, false en caso contrario
     */
    public boolean isDatabaseAvailable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception e) {
            logger.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Comprueba si Kafka está disponible (solo en modo no directo)
     *
     * @return true si Kafka está disponible o no es necesario, false en caso contrario
     */
    public boolean isKafkaAvailable() {
        if (directMode) {
            return true; // En modo directo, Kafka no es necesario
        }

        if (kafkaTemplate == null) {
            return false;
        }

        try {
            // Verificar la conexión a Kafka intentando obtener el broker list
            // En vez de usar getMetrics() que no está disponible
            kafkaTemplate.getProducerFactory().createProducer().partitionsFor("non-existent-topic-for-health-check");
            return true;
        } catch (Exception e) {
            // Si obtenemos un error específico sobre topic no encontrado, significa que
            // Kafka está disponible pero el topic no existe (lo cual es esperado)
            if (e.getMessage() != null &&
                    (e.getMessage().contains("Topic non-existent-topic-for-health-check not present") ||
                            e.getMessage().contains("Unknown topic"))) {
                return true;
            }

            logger.warn("Kafka health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Programa una verificación periódica del estado del sistema
     */
    @Scheduled(fixedRate = 60000) // Cada minuto
    public void checkSystemHealth() {
        logger.debug("Performing scheduled health check");

        boolean dbAvailable = isDatabaseAvailable();
        boolean kafkaAvailable = isKafkaAvailable();

        // Actualizar estado del servicio basado en las comprobaciones
        if (!dbAvailable) {
            setServiceStatus("unhealthy");
            logger.error("Service marked as unhealthy: Database unavailable");
        } else if (!directMode && !kafkaAvailable) {
            setServiceStatus("degraded");
            logger.warn("Service marked as degraded: Kafka unavailable");
        } else {
            setServiceStatus("healthy");
        }
    }

    /**
     * Obtiene un informe completo de salud del sistema
     *
     * @return Objeto HealthCheckResponse con el estado actual del sistema
     */
    public HealthCheckResponse getHealthCheckResponse() {
        String status = getServiceStatus();
        String kafkaStatus = directMode ? "disabled" : kafkaServers;
        String mode = directMode ? "direct" : "kafka";
        String hmacModuleStatus = hmacService.isModuleAvailable() ? "available" : "not available";
        String destUrlOverride = destinationUrlOverride != null ? destinationUrlOverride : "not set";

        // Crear respuesta según el estado
        HealthCheckResponse response;

        if ("healthy".equals(status)) {
            response = HealthCheckResponse.healthy(
                    appVersion, dbUrl, dbSchema, kafkaStatus,
                    mode, destUrlOverride, hmacModuleStatus
            );
        } else if ("degraded".equals(status)) {
            String reason = "Kafka unavailable";
            if (!isDatabaseAvailable()) {
                reason = "Database connection issues, but service operational";
            }

            response = HealthCheckResponse.degraded(
                    appVersion, dbUrl, dbSchema, kafkaStatus,
                    mode, destUrlOverride, hmacModuleStatus, reason
            );
        } else {
            // Unhealthy o cualquier otro estado
            response = HealthCheckResponse.unhealthy(
                    appVersion, "Critical service component unavailable"
            );
        }

        // Agregar métricas adicionales
        response.getMetrics().putAll(getAllCounters());

        return response;
    }

    /**
     * Obtiene información detallada sobre el estado de salud del servicio
     *
     * @return Mapa con información detallada sobre el estado de salud
     */
    public Map<String, Object> getDetailedHealthStatus() {
        Map<String, Object> healthDetails = new HashMap<>();

        // Estado general del servicio
        healthDetails.put("status", getServiceStatus());
        healthDetails.put("version", appVersion);

        // Estado de la base de datos
        boolean dbAvailable = isDatabaseAvailable();
        healthDetails.put("database", new HashMap<String, Object>() {{
            put("available", dbAvailable);
            put("url", dbUrl);
            put("schema", dbSchema);
        }});

        // Estado de Kafka
        boolean kafkaAvailable = isKafkaAvailable();
        healthDetails.put("kafka", new HashMap<String, Object>() {{
            put("available", kafkaAvailable);
            put("enabled", !directMode);
            put("servers", kafkaServers);
        }});

        // Configuración
        healthDetails.put("config", new HashMap<String, Object>() {{
            put("mode", directMode ? "direct" : "kafka");
            put("destinationUrlOverride", destinationUrlOverride != null ? destinationUrlOverride : "not set");
            put("hmacModule", hmacService.isModuleAvailable() ? "available" : "not available");
        }});

        // Métricas actuales
        healthDetails.put("metrics", getAllCounters());

        // Timestamp
        healthDetails.put("timestamp", java.time.LocalDateTime.now().toString());

        // Detalles adicionales (recursos del sistema)
        Runtime runtime = Runtime.getRuntime();
        healthDetails.put("system", new HashMap<String, Object>() {{
            put("processors", runtime.availableProcessors());
            put("memory", new HashMap<String, Object>() {{
                put("total", runtime.totalMemory());
                put("free", runtime.freeMemory());
                put("max", runtime.maxMemory());
                put("used", runtime.totalMemory() - runtime.freeMemory());
            }});
            try {
                healthDetails.put("disk", new HashMap<String, Object>() {{
                    java.io.File root = new java.io.File("/");
                    put("total", root.getTotalSpace());
                    put("free", root.getFreeSpace());
                    put("usable", root.getUsableSpace());
                }});
            } catch (Exception e) {
                // Omitir información de disco si no está disponible
                logger.debug("No se pudo obtener información del disco: {}", e.getMessage());
            }
        }});

        return healthDetails;
    }

    /**
     * Registra una entrega exitosa de webhook
     *
     * @param webhookConfigId ID de la configuración del webhook
     * @param statusCode Código de estado HTTP recibido
     * @param duration Duración de la operación en ms
     */
    public void recordSuccessfulDelivery(UUID webhookConfigId, int statusCode, long duration) {
        incrementCounter("messages.delivered");

        // Actualizar tiempos de respuesta
        long avgTime = getCounter("performance.avg_response_time_ms");
        long count = getCounter("messages.delivered");

        if (count == 1) {
            setCounter("performance.avg_response_time_ms", duration);
        } else {
            // Calcular nueva media
            long newAvg = (avgTime * (count - 1) + duration) / count;
            setCounter("performance.avg_response_time_ms", newAvg);
        }

        // Actualizar tiempo máximo si es necesario
        long maxTime = getCounter("performance.max_response_time_ms");
        if (duration > maxTime) {
            setCounter("performance.max_response_time_ms", duration);
        }

        // También podríamos actualizar contadores específicos por webhook
        incrementCounter("webhook." + webhookConfigId + ".delivered");

        // Actualizar contador específico por código de estado
        incrementCounter("status." + statusCode);
    }

    /**
     * Registra una entrega fallida de webhook
     *
     * @param webhookConfigId ID de la configuración del webhook
     */
    public void recordFailedDelivery(UUID webhookConfigId) {
        incrementCounter("messages.failed");

        // También podríamos actualizar contadores específicos por webhook
        incrementCounter("webhook." + webhookConfigId + ".failed");

        // Actualizar el contador total de fallos
        long totalFailed = getCounter("messages.failed");
        logger.debug("Total de mensajes fallidos: {}", totalFailed);
    }

    /**
     * Registra un intento fallido de entrega, pero que se reintentará
     *
     * @param webhookConfigId ID de la configuración del webhook
     * @param statusCode Código de estado HTTP recibido
     * @param duration Duración del intento en ms
     */
    public void recordFailedAttempt(UUID webhookConfigId, int statusCode, long duration) {
        incrementCounter("messages.failed_attempts");

        // Actualizar contador específico por webhook
        incrementCounter("webhook." + webhookConfigId + ".failed_attempts");

        // Actualizar contador específico por código de estado
        incrementCounter("status." + statusCode);

        // Actualizar estadísticas de tiempo de respuesta para intentos fallidos
        long avgFailedTime = getCounter("performance.avg_failed_time_ms");
        long failedCount = getCounter("messages.failed_attempts");

        if (failedCount == 1) {
            setCounter("performance.avg_failed_time_ms", duration);
        } else {
            // Calcular nueva media para tiempos de respuesta fallidos
            long newAvg = (avgFailedTime * (failedCount - 1) + duration) / failedCount;
            setCounter("performance.avg_failed_time_ms", newAvg);
        }
    }

    /**
     * Registra un error de conexión al intentar entregar un webhook
     *
     * @param webhookConfigId ID de la configuración del webhook
     */
    public void recordConnectionError(UUID webhookConfigId) {
        incrementCounter("messages.connection_errors");

        // Actualizar contador específico por webhook
        incrementCounter("webhook." + webhookConfigId + ".connection_errors");

        // También incrementamos el contador general de fallos
        incrementCounter("messages.failed");
    }
}