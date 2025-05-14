package com.sg.webhookservice.presentation.validation;

import com.sg.webhookservice.presentation.dto.WebhookConfigDto;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validador personalizado para WebhookConfigDto.
 * Realiza validaciones adicionales a las anotaciones de Jakarta Validation.
 */
@Component
public class WebhookConfigValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return WebhookConfigDto.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        WebhookConfigDto config = (WebhookConfigDto) target;

        // Validar estrategia de backoff
        if (config.getBackoffStrategy() != null) {
            String strategy = config.getBackoffStrategy().toLowerCase();
            if (!strategy.equals("linear") && !strategy.equals("exponential") && !strategy.equals("fixed")) {
                errors.rejectValue("backoffStrategy", "invalid.backoffStrategy",
                        "La estrategia de backoff debe ser 'linear', 'exponential' o 'fixed'");
            }

            // Validar factor de backoff para estrategia exponencial
            if (strategy.equals("exponential")) {
                if (config.getBackoffFactor() == null || config.getBackoffFactor() <= 1.0) {
                    errors.rejectValue("backoffFactor", "invalid.backoffFactor.exponential",
                            "El factor de backoff es obligatorio para la estrategia exponencial y debe ser mayor que 1.0");
                }
            }
        }

        // Validar intervalos
        if (config.getInitialInterval() != null && config.getMaxInterval() != null) {
            if (config.getMaxInterval() <= config.getInitialInterval()) {
                errors.rejectValue("maxInterval", "invalid.intervalRange",
                        "El intervalo máximo debe ser mayor que el intervalo inicial");
            }
        }

        // Validar URL
        if (config.getTargetUrl() != null) {
            try {
                new java.net.URL(config.getTargetUrl());
            } catch (java.net.MalformedURLException e) {
                errors.rejectValue("targetUrl", "invalid.url",
                        "La URL de destino no es válida");
            }
        }

        // Validar headers
        if (config.getHeaders() != null) {
            config.getHeaders().forEach((key, value) -> {
                if (key == null || key.isEmpty()) {
                    errors.rejectValue("headers", "invalid.headers.emptyKey",
                            "Las claves de los headers no pueden estar vacías");
                }
                if (value == null) {
                    errors.rejectValue("headers", "invalid.headers.nullValue",
                            "Los valores de los headers no pueden ser nulos");
                }
            });
        }
    }
}
