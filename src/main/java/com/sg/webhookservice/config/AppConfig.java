package com.sg.webhookservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Configuración general de la aplicación.
 * Define beans comunes utilizados en toda la aplicación como ObjectMapper, 
 * RestTemplate, ThreadPoolTaskExecutor, etc.
 */
@Configuration
public class AppConfig {

    @Value("${app.direct-mode:false}")
    private boolean directMode;

    @Value("${app.rest-template.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${app.rest-template.read-timeout:10000}")
    private int readTimeout;

    @Value("${app.thread-pool.core-size:5}")
    private int threadPoolCoreSize;

    @Value("${app.thread-pool.max-size:20}")
    private int threadPoolMaxSize;

    @Value("${app.thread-pool.queue-capacity:100}")
    private int threadPoolQueueCapacity;

    /**
     * Configuración específica de la aplicación webhook
     */
    @Bean
    @ConfigurationProperties(prefix = "app.webhook")
    public WebhookProperties webhookProperties() {
        return new WebhookProperties();
    }

    /**
     * Configuración de ObjectMapper personalizado para formato JSON
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .modules(new JavaTimeModule())
                .build();
    }

    /**
     * Configura RestTemplate para llamadas HTTP a APIs externas
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .requestFactory(this::clientHttpRequestFactory)
                .build();
    }

    /**
     * Configura fábrica de solicitudes HTTP con tiempos de espera configurables
     */
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    /**
     * Configura el executor de tareas asíncronas para procesamiento no bloqueante
     */
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolCoreSize);
        executor.setMaxPoolSize(threadPoolMaxSize);
        executor.setQueueCapacity(threadPoolQueueCapacity);
        executor.setThreadNamePrefix("webhook-async-");
        executor.initialize();
        return executor;
    }

    /**
     * Clase interna para propiedades específicas de webhook
     */
    public static class WebhookProperties {
        private String defaultSecret = "test-secret";
        private String destinationUrl;
        private int maxRetries = 3;
        private String backoffStrategy = "exponential";
        private int initialInterval = 60;
        private double backoffFactor = 2.0;
        private int maxInterval = 3600;
        private int maxAge = 86400;

        // Getters y setters
        public String getDefaultSecret() {
            return defaultSecret;
        }

        public void setDefaultSecret(String defaultSecret) {
            this.defaultSecret = defaultSecret;
        }

        public String getDestinationUrl() {
            return destinationUrl;
        }

        public void setDestinationUrl(String destinationUrl) {
            this.destinationUrl = destinationUrl;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public String getBackoffStrategy() {
            return backoffStrategy;
        }

        public void setBackoffStrategy(String backoffStrategy) {
            this.backoffStrategy = backoffStrategy;
        }

        public int getInitialInterval() {
            return initialInterval;
        }

        public void setInitialInterval(int initialInterval) {
            this.initialInterval = initialInterval;
        }

        public double getBackoffFactor() {
            return backoffFactor;
        }

        public void setBackoffFactor(double backoffFactor) {
            this.backoffFactor = backoffFactor;
        }

        public int getMaxInterval() {
            return maxInterval;
        }

        public void setMaxInterval(int maxInterval) {
            this.maxInterval = maxInterval;
        }

        public int getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(int maxAge) {
            this.maxAge = maxAge;
        }
    }
}