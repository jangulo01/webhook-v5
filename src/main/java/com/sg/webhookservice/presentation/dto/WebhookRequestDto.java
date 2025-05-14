package com.sg.webhookservice.presentation.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO para recibir las solicitudes de webhook entrantes.
 * Diseñado para ser flexible y aceptar cualquier estructura JSON.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de webhook entrante")
public class WebhookRequestDto {

    /**
     * Mapa interno para almacenar todas las propiedades JSON.
     */
    @JsonIgnore
    private Map<String, Object> payload = new HashMap<>();

    /**
     * URL de destino para el webhook.
     * Se utiliza para sobrescribir la URL configurada.
     */
    @Schema(description = "URL de destino (opcional, si es diferente a la configurada)")
    @JsonProperty("targetUrl")
    private String targetUrl;

    /**
     * Cabeceras HTTP personalizadas para la solicitud.
     */
    @Schema(description = "Cabeceras HTTP personalizadas (opcional)")
    @JsonProperty("headers")
    private Map<String, String> headers;

    /**
     * Indicador para la entrega inmediata del webhook.
     */
    @Schema(description = "Indica si el webhook debe ser entregado inmediatamente (opcional)")
    @JsonProperty("deliverImmediately")
    private boolean deliverImmediately;

    /**
     * Captura cualquier propiedad JSON y la almacena en el mapa interno.
     *
     * @param key Nombre de la propiedad JSON
     * @param value Valor de la propiedad JSON
     */
    @JsonAnySetter
    public void addProperty(String key, Object value) {
        // No guardar en payload las propiedades específicas que ya tienen campo
        if ("targetUrl".equals(key) || "headers".equals(key) || "deliverImmediately".equals(key)) {
            return;
        }
        payload.put(key, value);
    }

    /**
     * Expone todas las propiedades almacenadas en el mapa interno
     * para la serialización JSON.
     *
     * @return Mapa de propiedades
     */
    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return payload;
    }

    /**
     * Devuelve el mapa de datos completo, que representa
     * el payload del webhook.
     *
     * @return Mapa con todos los datos del webhook
     */
    @NotNull(message = "El payload del webhook no puede ser nulo")
    @Schema(description = "Datos del webhook", required = true)
    public Map<String, Object> getPayload() {
        return payload;
    }

    /**
     * Establece el mapa de datos completo.
     *
     * @param payload Mapa con los datos del webhook
     */
    public void setPayload(Map<String, Object> payload) {
        this.payload = payload != null ? payload : new HashMap<>();
    }

    /**
     * Verifica si el payload está vacío.
     *
     * @return true si el payload está vacío
     */
    @JsonIgnore
    public boolean isEmpty() {
        return payload == null || payload.isEmpty();
    }

    /**
     * Obtiene una propiedad específica del payload.
     *
     * @param key Nombre de la propiedad
     * @return Valor de la propiedad o null si no existe
     */
    @JsonIgnore
    public Object getProperty(String key) {
        return payload != null ? payload.get(key) : null;
    }

    /**
     * Obtiene una propiedad específica como String.
     *
     * @param key Nombre de la propiedad
     * @return Valor de la propiedad como String o null si no existe o no es String
     */
    @JsonIgnore
    public String getPropertyAsString(String key) {
        Object value = getProperty(key);
        return value instanceof String ? (String) value : null;
    }
}