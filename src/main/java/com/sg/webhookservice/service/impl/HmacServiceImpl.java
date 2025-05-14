package com.sg.webhookservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sg.webhookservice.presentation.exception.WebhookProcessingException;
import com.sg.webhookservice.service.HmacService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Implementación del servicio para generación y verificación de firmas HMAC.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HmacServiceImpl implements HmacService {

    private final ObjectMapper objectMapper;

    private static final String HASH_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    @Override
    public String normalizePayload(Object payload) {
        try {
            if (payload instanceof String payloadStr) {
                try {
                    // Intentar deserializar y luego volver a serializar de forma normalizada
                    JsonNode jsonNode = objectMapper.readTree(payloadStr);
                    return objectMapper.writeValueAsString(jsonNode);
                } catch (JsonProcessingException e) {
                    // Si no es JSON válido, devolver tal cual
                    return payloadStr;
                }
            } else {
                // Para objetos, convertir a JSON
                return objectMapper.writeValueAsString(payload);
            }
        } catch (Exception e) {
            log.error("Error normalizando payload: {}", e.getMessage(), e);
            return String.valueOf(payload);
        }
    }

    @Override
    public String generateSignature(Object payload, String secret) {
        return generateSignature(payload, secret, false);
    }

    @Override
    public String generateSignature(Object payload, String secret, boolean logDetails) {
        if (secret == null || secret.isBlank()) {
            throw new WebhookProcessingException(
                    "El secreto para la firma no puede estar vacío",
                    WebhookProcessingException.ProcessingPhase.SIGNATURE,
                    null,
                    null
            );
        }

        try {
            // Normalizar el payload para consistencia
            String normalizedPayload = normalizePayload(payload);

            if (logDetails) {
                log.info("Generando firma para payload normalizado: {}...",
                        normalizedPayload.length() > 100 ?
                                normalizedPayload.substring(0, 100) + "..." : normalizedPayload);
            }

            // Crear firma HMAC-SHA256
            Mac sha256Hmac = Mac.getInstance(HASH_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HASH_ALGORITHM);
            sha256Hmac.init(secretKey);

            byte[] hash = sha256Hmac.doFinal(normalizedPayload.getBytes(StandardCharsets.UTF_8));
            String signature = bytesToHex(hash);

            // Agregar prefijo para compatibilidad con formato estándar
            String completeSignature = SIGNATURE_PREFIX + signature;

            if (logDetails) {
                log.info("Firma generada: {}", completeSignature);
            }

            return completeSignature;

        } catch (NoSuchAlgorithmException e) {
            throw new WebhookProcessingException(
                    "Algoritmo de hash no disponible: " + HASH_ALGORITHM,
                    WebhookProcessingException.ProcessingPhase.SIGNATURE,
                    null,
                    null,
                    e.getMessage()
            );
        } catch (InvalidKeyException e) {
            throw new WebhookProcessingException(
                    "Clave secreta inválida para HMAC",
                    WebhookProcessingException.ProcessingPhase.SIGNATURE,
                    null,
                    null,
                    e.getMessage()
            );
        } catch (Exception e) {
            throw new WebhookProcessingException(
                    "Error generando firma HMAC",
                    e,
                    WebhookProcessingException.ProcessingPhase.SIGNATURE,
                    null,
                    null
            );
        }
    }

    @Override
    public boolean verifySignature(Object payload, String providedSignature, String secret) {
        return verifySignature(payload, providedSignature, secret, "unknown", false);
    }

    @Override
    public boolean verifySignature(
            Object payload,
            String providedSignature,
            String secret,
            String webhookName,
            boolean logDetails) {

        // Validar que la firma proporcionada no sea nula/vacía
        if (providedSignature == null || providedSignature.isBlank()) {
            if (logDetails) {
                log.warn("Firma recibida nula o vacía para webhook: {}", webhookName);
            }
            throw new WebhookProcessingException(
                    "Cabecera de firma faltante o vacía",
                    WebhookProcessingException.ProcessingPhase.SIGNATURE,
                    webhookName,
                    null
            );
        }

        // Validar formato básico de la firma
        if (!providedSignature.startsWith(SIGNATURE_PREFIX)) {
            if (logDetails) {
                log.warn("Formato de firma inválido: {}", providedSignature);
            }
            throw new WebhookProcessingException(
                    "Formato de firma inválido, debe comenzar con 'sha256='",
                    WebhookProcessingException.ProcessingPhase.SIGNATURE,
                    webhookName,
                    null
            );
        }

        try {
            // Generar firma esperada
            String expectedSignature = generateSignature(payload, secret, logDetails);

            // Comparar signatures con tiempo constante para evitar ataques de tiempo
            boolean isValid = MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    providedSignature.getBytes(StandardCharsets.UTF_8));

            if (logDetails) {
                if (isValid) {
                    log.info("✅ Firma válida para webhook: {}", webhookName);
                } else {
                    log.warn("❌ Firma inválida para webhook: {}", webhookName);
                    log.debug("Firma esperada: {}", expectedSignature);
                    log.debug("Firma recibida: {}", providedSignature);
                }
            }

            return isValid;

        } catch (WebhookProcessingException e) {
            throw e; // Re-lanzar excepciones específicas
        } catch (Exception e) {
            throw new WebhookProcessingException(
                    "Error verificando firma HMAC",
                    e,
                    WebhookProcessingException.ProcessingPhase.SIGNATURE,
                    webhookName,
                    null
            );
        }
    }

    @Override
    public String debugSignature(Object payload, String providedSignature, String secret) {
        StringBuilder report = new StringBuilder();
        report.append("🔍 DIAGNÓSTICO DE FIRMA 🔍\n");
        report.append("Firma recibida: ").append(providedSignature).append("\n");

        try {
            // Probar diferentes formatos de normalización

            // 1. Normalización estándar del servicio
            String normalizedPayload = normalizePayload(payload);
            String standardSignature = generateSignature(payload, secret, false);

            report.append("\n1. Firma con normalización estándar:\n");
            report.append("   Payload: ").append(truncateForReport(normalizedPayload)).append("\n");
            report.append("   Firma: ").append(standardSignature).append("\n");
            report.append("   Coincide: ").append(Objects.equals(providedSignature, standardSignature)).append("\n");

            // 2. Sin normalización (payload directo)
            String rawPayload = payload instanceof String ? (String)payload : String.valueOf(payload);
            String rawSignature = generateRawSignature(rawPayload, secret);

            report.append("\n2. Firma sin normalización:\n");
            report.append("   Payload: ").append(truncateForReport(rawPayload)).append("\n");
            report.append("   Firma: ").append(rawSignature).append("\n");
            report.append("   Coincide: ").append(Objects.equals(providedSignature, rawSignature)).append("\n");

            // 3. Normalización con espacios
            if (!(payload instanceof String)) {
                String prettyPayload = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
                String prettySignature = generateRawSignature(prettyPayload, secret);

                report.append("\n3. Firma con formato JSON pretty:\n");
                report.append("   Payload: ").append(truncateForReport(prettyPayload)).append("\n");
                report.append("   Firma: ").append(prettySignature).append("\n");
                report.append("   Coincide: ").append(Objects.equals(providedSignature, prettySignature)).append("\n");
            }

            // 4. Verificar si sólo falta el prefijo
            if (!providedSignature.startsWith(SIGNATURE_PREFIX)) {
                String withPrefix = SIGNATURE_PREFIX + providedSignature;
                report.append("\n4. Con prefijo agregado:\n");
                report.append("   Firma: ").append(withPrefix).append("\n");
                report.append("   Coincide con estándar: ").append(Objects.equals(withPrefix, standardSignature)).append("\n");
            }

            // Conclusión
            report.append("\n✅ SOLUCIÓN RECOMENDADA: ");
            if (Objects.equals(providedSignature, standardSignature)) {
                report.append("La firma coincide con el formato estándar. No se requieren cambios.");
            } else if (providedSignature.startsWith(SIGNATURE_PREFIX) &&
                    Objects.equals(providedSignature.substring(SIGNATURE_PREFIX.length()),
                            standardSignature.substring(SIGNATURE_PREFIX.length()))) {
                report.append("El hash es correcto pero existe alguna diferencia en el formato del prefijo.");
            } else {
                report.append("Usar el método de normalización estándar y verificar que el secreto sea idéntico en ambos sistemas.");
            }

        } catch (Exception e) {
            report.append("\n❌ ERROR DURANTE DIAGNÓSTICO: ").append(e.getMessage());
        }

        return report.toString();
    }

    /**
     * Genera una firma sin normalización (directamente del string)
     * Usado solo para diagnóstico de problemas
     */
    private String generateRawSignature(String rawPayload, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256Hmac = Mac.getInstance(HASH_ALGORITHM);
        SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), HASH_ALGORITHM);
        sha256Hmac.init(secretKey);

        byte[] hash = sha256Hmac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
        return SIGNATURE_PREFIX + bytesToHex(hash);
    }

    /**
     * Convierte bytes a representación hexadecimal.
     *
     * @param bytes Array de bytes
     * @return String en formato hexadecimal
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Trunca un string para incluirlo en un reporte de diagnóstico.
     */
    private String truncateForReport(String input) {
        if (input == null) {
            return "null";
        }
        if (input.length() <= 50) {
            return input;
        }
        return input.substring(0, 47) + "...";
    }
}
