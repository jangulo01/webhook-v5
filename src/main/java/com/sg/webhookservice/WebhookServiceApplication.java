package com.sg.webhookservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Punto de entrada principal para la aplicación de servicio de webhooks
 * 
 * Esta aplicación Spring Boot proporciona:
 * - Recepción y procesamiento de webhooks
 * - Integración con Kafka para procesamiento asíncrono
 * - Envío directo a APIs destino
 * - Reintentos configurables con estrategias de backoff
 * - Endpoints administrativos para gestión y monitoreo
 * - Implementación de seguridad con firma HMAC
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableKafka
@ConfigurationPropertiesScan
public class WebhookServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebhookServiceApplication.class, args);
	}
}