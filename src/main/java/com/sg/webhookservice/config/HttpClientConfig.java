package com.sg.webhookservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuración para crear y configurar beans de HTTP y conexión.
 */
@Configuration
public class HttpClientConfig {

    /**
     * Crea un bean RestTemplate con configuración optimizada para webhooks.
     *
     * @param builder RestTemplateBuilder inyectado automáticamente
     * @return RestTemplate configurado
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}