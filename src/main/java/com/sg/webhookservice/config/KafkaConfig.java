package com.sg.webhookservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de Kafka para el servicio de webhooks
 * Establece la configuración para productores y consumidores de Kafka,
 * y define los tópicos necesarios.
 */
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Value("${kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${kafka.topic.webhook-events:webhook-events}")
    private String webhookEventsTopic;
    
    @Value("${kafka.topic.webhook-retries:webhook-retries}")
    private String webhookRetriesTopic;
    
    @Value("${kafka.consumer.group-id:webhook-processor-group}")
    private String consumerGroupId;
    
    @Value("${kafka.consumer.retry-group-id:webhook-retry-processor-group}")
    private String retryConsumerGroupId;
    
    @Value("${kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;
    
    /**
     * Configura la fábrica de productores de Kafka
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Aumentar confiabilidad
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * Crea el template de Kafka para enviar mensajes
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    /**
     * Configura la fábrica de consumidores de Kafka para eventos de webhook
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        // Evitar commit automático para mejor control
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * Configura la fábrica de consumidores de Kafka para reintentos
     */
    @Bean
    public ConsumerFactory<String, String> retryConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, retryConsumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * Configura el contenedor de listeners para eventos de webhook
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setMessageConverter(new StringJsonMessageConverter());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3); // Número de hilos consumidores
        
        // Configurar el manejador de errores con backoff exponencial
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(60000L); // 1 minuto máximo entre reintentos
        backOff.setMaxElapsedTime(600000L); // 10 minutos tiempo máximo total
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            // Aquí podríamos enviar a un tópico de dead-letter o logear
        }, backOff);
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
    
    /**
     * Configura el contenedor de listeners para mensajes de reintento
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> retryKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(retryConsumerFactory());
        factory.setMessageConverter(new StringJsonMessageConverter());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(2); // Menos concurrencia para reintentos
        
        // Configurar el manejador de errores con backoff exponencial más agresivo
        ExponentialBackOff backOff = new ExponentialBackOff(5000L, 2.5);
        backOff.setMaxInterval(120000L); // 2 minutos máximo entre reintentos
        backOff.setMaxElapsedTime(900000L); // 15 minutos tiempo máximo total
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            // Aquí podríamos implementar una lógica de fallback
        }, backOff);
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
    
    /**
     * Define el tópico de eventos de webhook
     */
    @Bean
    public NewTopic webhookEventsTopic() {
        return new NewTopic(webhookEventsTopic, 3, (short) 1);
    }
    
    /**
     * Define el tópico de reintentos de webhook
     */
    @Bean
    public NewTopic webhookRetriesTopic() {
        return new NewTopic(webhookRetriesTopic, 3, (short) 1);
    }
}