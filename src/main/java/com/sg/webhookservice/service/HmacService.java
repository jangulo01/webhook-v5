package com.sg.webhookservice.service;

/**
 * Interfaz para el servicio de generación y verificación de firmas HMAC.
 * Proporciona funcionalidad para firmar y verificar webhooks.
 */
public interface HmacService {

    /**
     * Normaliza el payload para garantizar serialización consistente.
     *
     * @param payload El payload a normalizar, puede ser un objeto, Map o string JSON
     * @return El payload normalizado como string
     */
    String normalizePayload(Object payload);

    /**
     * Genera una firma HMAC-SHA256 para un payload.
     *
     * @param payload Payload para firmar
     * @param secret Secreto a usar para la firma
     * @return Firma generada en formato hexadecimal con prefijo "sha256="
     * @throws com.sg.webhookservice.presentation.exception.WebhookProcessingException si hay error en el proceso
     */
    String generateSignature(Object payload, String secret);

    /**
     * Genera una firma HMAC-SHA256 con opciones de logging.
     *
     * @param payload Payload para firmar
     * @param secret Secreto a usar para la firma
     * @param logDetails Si se deben registrar detalles para debugging
     * @return Firma generada en formato hexadecimal con prefijo "sha256="
     * @throws com.sg.webhookservice.presentation.exception.WebhookProcessingException si hay error en el proceso
     */
    String generateSignature(Object payload, String secret, boolean logDetails);

    /**
     * Verifica una firma HMAC contra un payload.
     *
     * @param payload Payload a verificar
     * @param providedSignature Firma a comprobar
     * @param secret Secreto para verificación
     * @return true si la firma es válida, false si no
     * @throws com.sg.webhookservice.presentation.exception.WebhookProcessingException si hay error en el proceso
     */
    boolean verifySignature(Object payload, String providedSignature, String secret);

    /**
     * Verifica una firma HMAC con opciones detalladas.
     *
     * @param payload Payload a verificar
     * @param providedSignature Firma a comprobar
     * @param secret Secreto para verificación
     * @param webhookName Nombre del webhook (para logging)
     * @param logDetails Si se deben registrar detalles
     * @return true si la firma es válida, false si no
     * @throws com.sg.webhookservice.presentation.exception.WebhookProcessingException si hay error en el proceso
     */
    boolean verifySignature(
            Object payload,
            String providedSignature,
            String secret,
            String webhookName,
            boolean logDetails);

    /**
     * Depura problemas de firma probando diferentes formatos.
     * Útil para diagnosticar incompatibilidades entre sistemas.
     *
     * @param payload Payload original
     * @param providedSignature Firma problemática
     * @param secret Secreto utilizado
     * @return Informe detallado del análisis
     */
    String debugSignature(Object payload, String providedSignature, String secret);
}