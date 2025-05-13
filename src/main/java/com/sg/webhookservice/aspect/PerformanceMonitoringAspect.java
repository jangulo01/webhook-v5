package com.sg.webhookservice.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aspecto para monitoreo de rendimiento que:
 * - Mide tiempos de ejecución de componentes críticos
 * - Identifica operaciones lentas
 * - Recolecta métricas para análisis de patrones
 * - Alimenta métricas para actuator/prometheus
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class PerformanceMonitoringAspect {
    
    private final MeterRegistry meterRegistry;
    
    // Caché para conteos de llamadas a métodos
    private final ConcurrentHashMap<String, AtomicLong> methodCallCounts = new ConcurrentHashMap<>();
    
    // Umbral para considerar una operación como lenta (en milisegundos)
    @Value("${monitoring.slow-execution-threshold-ms:500}")
    private long slowExecutionThresholdMs;
    
    // Nivel de advertencia para operaciones extremadamente lentas (en milisegundos)
    @Value("${monitoring.critical-execution-threshold-ms:2000}")
    private long criticalExecutionThresholdMs;
    
    /**
     * Pointcut para servicios
     */
    @Pointcut("execution(* com.sg.webhookservice.service.*.*(..))")
    public void serviceMethods() {}
    
    /**
     * Pointcut para repositorios (operaciones de base de datos)
     */
    @Pointcut("execution(* com.sg.webhookservice.repository.*.*(..))")
    public void repositoryMethods() {}
    
    /**
     * Pointcut para consumidores de Kafka
     */
    @Pointcut("execution(* com.sg.webhookservice.kafka.consumer.*.*(..))")
    public void kafkaConsumerMethods() {}
    
    /**
     * Pointcut para envíos de webhook (operaciones externas)
     */
    @Pointcut("execution(* com.sg.webhookservice.service.MessageSenderService.send*(..))")
    public void webhookSenderMethods() {}
    
    /**
     * Monitorear el rendimiento de todos los servicios
     */
    @Around("serviceMethods()")
    public Object monitorServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorPerformance(joinPoint, "Service");
    }
    
    /**
     * Monitorear el rendimiento de los repositorios (operaciones de base de datos)
     */
    @Around("repositoryMethods()")
    public Object monitorRepositoryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorPerformance(joinPoint, "Repository");
    }
    
    /**
     * Monitorear el rendimiento de los consumidores de Kafka
     */
    @Around("kafkaConsumerMethods()")
    public Object monitorKafkaConsumerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorPerformance(joinPoint, "KafkaConsumer");
    }
    
    /**
     * Monitorear el rendimiento de las operaciones de envío de webhook
     */
    @Around("webhookSenderMethods()")
    public Object monitorWebhookSenderPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorPerformance(joinPoint, "WebhookSender");
    }
    
    /**
     * Método común para monitoreo de rendimiento con diferentes categorías
     */
    private Object monitorPerformance(ProceedingJoinPoint joinPoint, String category) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();
        
        // Clave única para identificar el método
        String methodKey = className + "." + methodName;
        
        // Incrementar contador de llamadas al método
        AtomicLong callCount = methodCallCounts.computeIfAbsent(methodKey, k -> new AtomicLong(0));
        long currentCount = callCount.incrementAndGet();
        
        // Controlar frecuencia de logs para métodos muy frecuentes
        boolean logEnabled = shouldLogExecution(currentCount);
        
        // Registrar entrada si está habilitado el log
        if (logEnabled && log.isDebugEnabled()) {
            log.debug("Executing {} method {}.{}", category, className, methodName);
        }
        
        // Crear timer para métricas
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Proceder con la ejecución del método
            return joinPoint.proceed();
        } finally {
            // Calcular tiempo de ejecución en milisegundos
            long executionTime = sample.stop(createTimer(methodKey, category, className, methodName));
            long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(executionTime);
            
            // Loguear información de rendimiento basada en umbrales
            if (executionTimeMs >= criticalExecutionThresholdMs) {
                log.warn("CRITICAL PERFORMANCE: {} {}.{} took {} ms to execute", 
                        category, className, methodName, executionTimeMs);
            } else if (executionTimeMs >= slowExecutionThresholdMs) {
                log.warn("SLOW EXECUTION: {} {}.{} took {} ms to execute", 
                        category, className, methodName, executionTimeMs);
            } else if (logEnabled && log.isDebugEnabled()) {
                log.debug("{} {}.{} executed in {} ms", 
                        category, className, methodName, executionTimeMs);
            }
        }
    }
    
    /**
     * Determina si debemos loguear una ejecución basado en el conteo de llamadas
     * Para evitar inundar los logs con métodos llamados con mucha frecuencia
     */
    private boolean shouldLogExecution(long callCount) {
        if (callCount <= 10) {
            // Loguear las primeras 10 llamadas
            return true;
        } else if (callCount <= 100) {
            // Loguear cada 10 llamadas hasta 100
            return callCount % 10 == 0;
        } else if (callCount <= 1000) {
            // Loguear cada 100 llamadas hasta 1000
            return callCount % 100 == 0;
        } else {
            // Loguear cada 1000 llamadas después de 1000
            return callCount % 1000 == 0;
        }
    }
    
    /**
     * Crea un timer para métricas con etiquetas apropiadas
     */
    private Timer createTimer(String methodKey, String category, String className, String methodName) {
        List<Tag> tags = Arrays.asList(
            Tag.of("category", category),
            Tag.of("class", className),
            Tag.of("method", methodName)
        );
        
        return Timer.builder("webhook.execution.time")
                .tags(tags)
                .description("Tiempo de ejecución de métodos categorizados")
                .register(meterRegistry);
    }
}