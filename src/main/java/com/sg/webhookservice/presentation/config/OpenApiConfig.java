package com.sg.webhookservice.presentation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Configuración para la documentación de la API con OpenAPI 3.0 (Swagger).
 */
@Configuration
public class OpenApiConfig {

    @Value("${springdoc.api-docs.path:/v3/api-docs}")
    private String apiDocsPath;

    @Value("${application.version:1.0.0}")
    private String appVersion;

    @Value("${application.name:Webhook Service}")
    private String appName;

    @Value("${application.description:Servicio para gestión y entrega de webhooks}")
    private String appDescription;

    /**
     * Configura la documentación OpenAPI.
     *
     * @return Configuración OpenAPI
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(Arrays.asList(
                        new Server().url("/").description("Current Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearer-key",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .in(SecurityScheme.In.HEADER)
                                        .name("Authorization")
                        ))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearer-key")
                );
    }

    /**
     * Configura la información general de la API.
     *
     * @return Información de la API
     */
    private Info apiInfo() {
        return new Info()
                .title(appName)
                .description(appDescription)
                .version(appVersion)
                .contact(new Contact()
                        .name("Soporte Técnico")
                        .email("support@spergiros.com.co")
                        .url("https://supergiros.com.co/support"))
                .license(new License()
                        .name("Licencia Propietaria")
                        .url("https://supergiros.com.co/license"));
    }
}