package com.sg.webhookservice.dto;

import java.util.Objects;

/**
 * DTO para las solicitudes de reintento en lote de mensajes fallidos
 */
public class BulkRetryRequest {

    /**
     * Número de horas hacia atrás para buscar mensajes fallidos
     * Valor por defecto: 24 horas
     */
    private Integer hours = 24;

    /**
     * URL de destino para sobrescribir la configurada en los webhooks
     * Opcional, si no se proporciona se usa la URL original de cada webhook
     */
    private String destinationUrl;

    /**
     * Límite de mensajes a reintentar
     * Valor por defecto: 100 mensajes
     */
    private Integer limit = 100;

    /**
     * Constructor por defecto
     */
    public BulkRetryRequest() {
    }

    /**
     * Constructor con todos los campos
     *
     * @param hours Número de horas hacia atrás para buscar mensajes
     * @param destinationUrl URL de destino para sobrescribir (opcional)
     * @param limit Límite de mensajes a reintentar
     */
    public BulkRetryRequest(Integer hours, String destinationUrl, Integer limit) {
        this.hours = hours;
        this.destinationUrl = destinationUrl;
        this.limit = limit;
    }

    /**
     * Obtiene el número de horas hacia atrás
     *
     * @return Número de horas
     */
    public Integer getHours() {
        return hours;
    }

    /**
     * Establece el número de horas hacia atrás
     *
     * @param hours Número de horas
     */
    public void setHours(Integer hours) {
        this.hours = hours;
    }

    /**
     * Obtiene la URL de destino sobrescrita
     *
     * @return URL de destino o null si no está definida
     */
    public String getDestinationUrl() {
        return destinationUrl;
    }

    /**
     * Establece la URL de destino sobrescrita
     *
     * @param destinationUrl URL de destino
     */
    public void setDestinationUrl(String destinationUrl) {
        this.destinationUrl = destinationUrl;
    }

    /**
     * Obtiene el límite de mensajes a reintentar
     *
     * @return Límite de mensajes
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * Establece el límite de mensajes a reintentar
     *
     * @param limit Límite de mensajes
     */
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BulkRetryRequest that = (BulkRetryRequest) o;
        return Objects.equals(hours, that.hours) &&
                Objects.equals(destinationUrl, that.destinationUrl) &&
                Objects.equals(limit, that.limit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hours, destinationUrl, limit);
    }

    @Override
    public String toString() {
        return "BulkRetryRequest{" +
                "hours=" + hours +
                ", destinationUrl='" + destinationUrl + '\'' +
                ", limit=" + limit +
                '}';
    }
}