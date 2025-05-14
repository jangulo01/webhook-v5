package com.sg.webhookservice.presentation.validation;

import com.sg.webhookservice.presentation.dto.WebhookRequestDto;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validador personalizado para WebhookRequestDto.
 * Verifica que el payload recibido sea válido.
 */
@Component
public class WebhookRequestValidator implements Validator {

    private static final int MAX_PAYLOAD_SIZE = 1_048_576; // 1 MB

    @Override
    public boolean supports(Class<?> clazz) {
        return WebhookRequestDto.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        WebhookRequestDto request = (WebhookRequestDto) target;

        // Verificar que el payload no sea nulo o vacío
        if (request.getPayload() == null || request.getPayload().isEmpty()) {
            errors.reject("payload.empty", "El payload del webhook no puede estar vacío");
            return;
        }

        // Verificar tamaño máximo (estimado)
        int estimatedSize = estimatePayloadSize(request.getPayload());
        if (estimatedSize > MAX_PAYLOAD_SIZE) {
            errors.reject("payload.tooLarge",
                    "El payload excede el tamaño máximo permitido de 1 MB");
        }

        // Verificar estructura válida (esto dependerá de los requisitos específicos)
        if (!hasValidStructure(request)) {
            errors.reject("payload.invalidStructure",
                    "El payload tiene una estructura inválida");
        }
    }

    /**
     * Verifica si la estructura del payload es válida según las reglas de negocio.
     * Esta implementación es un ejemplo; las reglas reales dependerán de los requisitos.
     *
     * @param request La solicitud de webhook
     * @return true si la estructura es válida
     */
    private boolean hasValidStructure(WebhookRequestDto request) {
        // Implementación de ejemplo - podría verificar campos obligatorios,
        // formato de datos, etc.

        // Siempre válido para esta implementación básica
        return true;
    }

    /**
     * Estima el tamaño del payload en bytes.
     * Esta es una estimación ya que el tamaño real en memoria
     * puede variar según la implementación de Java.
     *
     * @param payload El payload a evaluar
     * @return Tamaño estimado en bytes
     */
    private int estimatePayloadSize(Object payload) {
        if (payload == null) {
            return 0;
        }

        int size = 0;

        if (payload instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) payload;
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                // Estimar tamaño de la clave
                if (entry.getKey() instanceof String) {
                    size += ((String) entry.getKey()).length() * 2; // Caracteres UTF-16
                }

                // Estimar tamaño del valor
                Object value = entry.getValue();
                if (value instanceof String) {
                    size += ((String) value).length() * 2; // Caracteres UTF-16
                } else if (value instanceof Number) {
                    size += 8; // Aproximado para números
                } else if (value instanceof Boolean) {
                    size += 1;
                } else if (value instanceof java.util.Map || value instanceof java.util.Collection) {
                    size += estimatePayloadSize(value);
                } else if (value != null) {
                    // Para otros tipos, usar una estimación basada en toString()
                    size += value.toString().length() * 2;
                }
            }

            // Overhead de Map
            size += map.size() * 32;
        } else if (payload instanceof java.util.Collection) {
            java.util.Collection<?> collection = (java.util.Collection<?>) payload;
            for (Object item : collection) {
                size += estimatePayloadSize(item);
            }

            // Overhead de Collection
            size += collection.size() * 16;
        } else if (payload instanceof String) {
            size += ((String) payload).length() * 2; // Caracteres UTF-16
        } else {
            // Para otros tipos, usar una estimación basada en toString()
            size += payload.toString().length() * 2;
        }

        return size;
    }
}