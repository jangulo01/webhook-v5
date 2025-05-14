package com.sg.webhookservice.dto;

import java.util.Objects;

/**
 * DTO para las solicitudes de envío directo de mensajes pendientes
 */
public class DirectSendRequest {

    /**
     * Estado de los mensajes a enviar: 'pending', 'failed', o 'all'
     * Valor por defecto: 'pending'
     */
    private String status = "pending";

    /**
     * URL de destino para sobrescribir la configurada en los webhooks
     * Opcional, si no se proporciona se usa la URL original de cada webhook
     */
    private String destinationUrl;

    /**
     * Secreto para la firma HMAC de los mensajes
     * Opcional, si no se proporciona se usa el secreto original de cada webhook
     */
    private String secret;

    /**
     * Límite de mensajes a enviar
     * Valor por defecto: 100 mensajes
     */
    private Integer limit = 100;

    /**
     * Constructor por defecto
     */
    public DirectSendRequest() {
    }

    /**
     * Constructor con todos los campos
     *
     * @param status Estado de los mensajes a enviar ('pending', 'failed', 'all')
     * @param destinationUrl URL de destino para sobrescribir (opcional)
     * @param secret Secreto para la firma HMAC (opcional)
     * @param limit Límite de mensajes a enviar
     */
    public DirectSendRequest(String status, String destinationUrl, String secret, Integer limit) {
        this.status = status;
        this.destinationUrl = destinationUrl;
        this.secret = secret;
        this.limit = limit;
    }

    /**
     * Obtiene el estado de los mensajes a enviar
     *
     * @return Estado de los mensajes ('pending', 'failed', 'all')
     */
    public String getStatus() {
        return status;
    }

    /**
     * Establece el estado de los mensajes a enviar
     *
     * @param status Estado de los mensajes ('pending', 'failed', 'all')
     */
    public void setStatus(String status) {
        this.status = status;
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
     * Obtiene el secreto para la firma HMAC
     *
     * @return Secreto o null si no está definido
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Establece el secreto para la firma HMAC
     *
     * @param secret Secreto
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * Obtiene el límite de mensajes a enviar
     *
     * @return Límite de mensajes
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * Establece el límite de mensajes a enviar
     *
     * @param limit Límite de mensajes
     */
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    /**
     * Valida si el estado es válido
     *
     * @return true si el estado es 'pending', 'failed' o 'all', false en caso contrario
     */
    public boolean isValidStatus() {
        return "pending".equals(status) || "failed".equals(status) || "all".equals(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectSendRequest that = (DirectSendRequest) o;
        return Objects.equals(status, that.status) &&
                Objects.equals(destinationUrl, that.destinationUrl) &&
                Objects.equals(secret, that.secret) &&
                Objects.equals(limit, that.limit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, destinationUrl, secret, limit);
    }

    @Override
    public String toString() {
        return "DirectSendRequest{" +
                "status='" + status + '\'' +
                ", destinationUrl='" + destinationUrl + '\'' +
                // No incluimos el secreto en toString por seguridad
                ", hasSecret=" + (secret != null) +
                ", limit=" + limit +
                '}';
    }
}