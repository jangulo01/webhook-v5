package com.sg.webhookservice.scheduler;

import com.yourcompany.webhookservice.exception.WebhookProcessingException;
import com.yourcompany.webhookservice.kafka.producer.KafkaProducerService;
import com.yourcompany.webhookservice.model.Message;
import com.yourcompany.webhookservice.repository.MessageRepository;
import com.yourcompany.webhookservice.service.MessageProcessingService;
import com.yourcompany.webhookservice.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Componente que programa y ejecuta reintentos para mensajes de webhook fallidos.
 * 
 * Periódicamente escanea la base de datos para encontrar mensajes que necesitan
 * ser reintentados y los procesa según la estrategia de reintentos configurada.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RetryScheduler {

    private final MessageRepository messageRepository;
    private final KafkaProducerService kafkaProducerService;
    private final MessageProcessingService messageProcessingService;
    private final RetryService retryService;
    
    // Configuración de reintentos
    @Value("${retry.scheduler.batch-size:100}")
    private int batchSize;
    
    @Value("${retry.scheduler.enabled:true}")
    private boolean retryEnabled;
    
    @Value("${retry.parallel-processing:true}")
    private boolean parallelProcessing;
    
    @Value("${retry.parallel-threads:5}")
    private int parallelThreads;
    
    @Value("${app.direct-mode:false}")
    private boolean directMode;
    
    /**
     * Tarea programada para verificar y procesar mensajes listos para reintento.
     * Se ejecuta cada 30 segundos.
     */
    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    @Transactional(readOnly = true)  // Solo lectura para la identificación, procesamiento en otra transacción
    public void processRetryableMessages() {
        if (!retryEnabled) {
            log.debug("Programador de reintentos desactivado por configuración");
            return;
        }
        
        // Encontrar mensajes que necesitan reintento
        OffsetDateTime now = OffsetDateTime.now();
        List<Message> messagesToRetry = messageRepository.findMessagesReadyForRetry(now, batchSize);
        
        if (messagesToRetry.isEmpty()) {
            log.debug("No se encontraron mensajes listos para reintento");
            return;
        }
        
        log.info("Encontrados {} mensajes listos para reintento", messagesToRetry.size());
        
        // Extraer IDs para procesamiento
        List<UUID> messageIds = messagesToRetry.stream()
                .map(Message::getId)
                .toList();
        
        // Procesar reintentos
        if (parallelProcessing) {
            processInParallel(messageIds);
        } else {
            processSequentially(messageIds);
        }
    }
    
    /**
     * Procesa los reintentos en paralelo utilizando un pool de hilos.
     * 
     * @param messageIds IDs de mensajes a reintentar
     */
    private void processInParallel(List<UUID> messageIds) {
        log.info("Procesando {} reintentos en paralelo con {} hilos", 
                messageIds.size(), parallelThreads);
        
        ExecutorService executor = Executors.newFixedThreadPool(parallelThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (UUID messageId : messageIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    processRetry(messageId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("Error en reintento paralelo para mensaje {}: {}", 
                            messageId, e.getMessage(), e);
                    failureCount.incrementAndGet();
                }
            }, executor);
            
            futures.add(future);
        }
        
        // Esperar a que todos los reintentos terminen
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        log.info("Procesamiento paralelo de reintentos completado. Éxitos: {}, Fallos: {}", 
                successCount.get(), failureCount.get());
    }
    
    /**
     * Procesa los reintentos secuencialmente.
     * 
     * @param messageIds IDs de mensajes a reintentar
     */
    private void processSequentially(List<UUID> messageIds) {
        log.info("Procesando {} reintentos secuencialmente", messageIds.size());
        
        int successCount = 0;
        int failureCount = 0;
        
        for (UUID messageId : messageIds) {
            try {
                processRetry(messageId);
                successCount++;
            } catch (Exception e) {
                log.error("Error en reintento secuencial para mensaje {}: {}", 
                        messageId, e.getMessage(), e);
                failureCount++;
            }
        }
        
        log.info("Procesamiento secuencial de reintentos completado. Éxitos: {}, Fallos: {}", 
                successCount, failureCount);
    }
    
    /**
     * Procesa un reintento individual.
     * 
     * @param messageId ID del mensaje a reintentar
     */
    private void processRetry(UUID messageId) {
        try {
            if (directMode) {
                // Procesar directamente sin Kafka
                log.debug("Procesando reintento directo para mensaje {}", messageId);
                messageProcessingService.processRetry(messageId);
            } else {
                // Enviar a Kafka para procesamiento
                log.debug("Enviando reintento a Kafka para mensaje {}", messageId);
                kafkaProducerService.sendRetryMessage(messageId.toString());
            }
        } catch (Exception e) {
            // Actualizar el estado para reflejar el error en el reintento
            retryService.handleRetryError(messageId, e);
            
            throw new WebhookProcessingException(
                    "Error al programar reintento para mensaje",
                    e,
                    WebhookProcessingException.ProcessingPhase.RETRY_SCHEDULING,
                    null,
                    messageId.toString()
            );
        }
    }
    
    /**
     * Tarea programada para verificar y actualizar retrasos dinámicos.
     * Ajusta los tiempos de reintento basado en el estado de destinos.
     * Ejecutada cada 5 minutos.
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void updateDynamicBackoffs() {
        if (!retryEnabled) {
            return;
        }
        
        log.info("Actualizando retrasos dinámicos para destinos con problemas...");
        
        // Permitir que el servicio de reintentos ajuste backoffs dinámicamente
        int updatedCount = retryService.updateDynamicBackoffs();
        
        if (updatedCount > 0) {
            log.info("Se actualizaron retrasos dinámicos para {} destinos", updatedCount);
        } else {
            log.debug("No se requirieron actualizaciones de retrasos dinámicos");
        }
    }
    
    /**
     * Tarea programada para generar estadísticas de reintentos.
     * Ejecutada cada hora.
     */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void generateRetryStatistics() {
        if (!retryEnabled) {
            return;
        }
        
        log.info("Generando estadísticas de reintentos...");
        
        // Obtener y registrar estadísticas de reintentos
        retryService.logRetryStatistics();
    }
    
    /**
     * Inicializa el procesamiento de reintentos pendientes al arrancar la aplicación.
     * Este método es llamado por un componente de inicio (no mostrado aquí).
     */
    public void processInitialRetries() {
        if (!retryEnabled) {
            log.info("Procesamiento inicial de reintentos desactivado");
            return;
        }
        
        log.info("Iniciando procesamiento inicial de reintentos pendientes...");
        
        // Procesar reintentos pendientes
        OffsetDateTime now = OffsetDateTime.now();
        List<Message> pendingRetries = messageRepository.findMessagesReadyForRetry(now, 500);
        
        if (pendingRetries.isEmpty()) {
            log.info("No se encontraron reintentos pendientes iniciales");
            return;
        }
        
        log.info("Encontrados {} reintentos pendientes al iniciar", pendingRetries.size());
        
        // Programar procesamiento
        List<UUID> messageIds = pendingRetries.stream()
                .map(Message::getId)
                .toList();
        
        // En inicialización, siempre usar modo paralelo para mayor eficiencia
        processInParallel(messageIds);
    }
}