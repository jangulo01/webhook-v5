package com.sg.webhookservice.dto;

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
     * Mapa interno para almacenar propiedades dinámicas.
     * Se utiliza Jackson @JsonAnySetter/@JsonAnyGetter para 
     * soportar cualquier estructura JSON como parte del payload.
     */
    @JsonIgnore
    private Map<String, Object> dataMap = new HashMap<>();

    /**
     * Captura cualquier propiedad JSON que no sea explícitamente mapeada
     * a otro campo y la almacena en el mapa interno.
     * 
     * @param key Nombre de la propiedad JSON
     * @param value Valor de la propiedad JSON
     */
    @JsonAnySetter
    public void addProperty(String key, Object value) {
        dataMap.put(key, value);
    }

    /**
     * Expone todas las propiedades almacenadas en el mapa interno
     * para la serialización JSON.
     * 
     * @return Mapa de propiedades dinámicas
     */
    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return dataMap;
    }

    /**
     * Devuelve el mapa de datos completo, que representa
     * el payload del webhook.
     * 
     * @return Mapa con todos los datos del webhook
     */
    @NotNull(message = "El payload del webhook no puede ser nulo")
    @Schema(description = "Datos del webhook", required = true)
    @JsonIgnore
    public Map<String, Object> getData() {
        return dataMap;
    }

    /**
     * Establece el mapa de datos completo.
     * 
     * @param data Mapa con los datos del webhook
     */
    @JsonIgnore
    public void setData(Map<String, Object> data) {
        this.dataMap = data != null ? data : new HashMap<>();
    }

    /**
     * Método de conveniencia para verificar si el payload está vacío.
     * 
     * @return true si el payload está vacío, false en caso contrario
     */
    @JsonIgnore
    public boolean isEmpty() {
        return dataMap == null || dataMap.isEmpty();
    }

    /**
     * Método de conveniencia para obtener una propiedad específica del payload.
     * 
     * @param key Nombre de la propiedad
     * @return Valor de la propiedad o null si no existe
     */
    @JsonIgnore
    public Object getProperty(String key) {
        return dataMap != null ? dataMap.get(key) : null;
    }

    /**
     * Método de conveniencia para obtener una propiedad específica como String.
     * 
     * @param key Nombre de la propiedad
     * @return Valor de la propiedad como String o null si no existe o no es String
     */
    @JsonIgnore
    public String getPropertyAsString(String key) {
        Object value = getProperty(key);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Método de ayuda para determinar si una property es un tipo específico.
     * Útil para navegación segura en estructuras JSON complejas.
     *
     * @param key Nombre de la propiedad
     * @param clazz Clase esperada
     * @return true si la propiedad existe y es del tipo especificado
     */
    @JsonIgnore
    public <T> boolean isPropertyOfType(String key, Class<T> clazz) {
        Object value = getProperty(key);
        return value != null && clazz.isInstance(value);
    }

    /**
     * Método de conveniencia para obtener una propiedad anidada usando notación de puntos.
     * Por ejemplo, "data.user.name" buscará dataMap.get("data").get("user").get("name")
     *
     * @param path Ruta con notación de puntos
     * @return Valor encontrado o null si no existe o no se puede navegar la ruta
     */
    @JsonIgnore
    public Object getNestedProperty(String path) {
        if (path == null || path.isEmpty() || dataMap == null) {
            return null;
        }

        String[] parts = path.split("\\.");
        Object current = dataMap;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Examina el payload para verificar si tiene la estructura esperada.
     * Este método es útil para validaciones personalizadas.
     *
     * @return true si el payload tiene una estructura válida según criterios definidos
     */
    @JsonIgnore
    public boolean hasValidStructure() {
        // La implementación varía según los requisitos específicos.
        // Por ejemplo, podríamos validar que el payload tenga ciertos campos obligatorios.
        return dataMap != null && !dataMap.isEmpty();
    }
}