package com.sg.webhookservice.service;

import com.sg.webhookservice.presentation.dto.BulkRetryRequestDto;
import com.sg.webhookservice.presentation.dto.BulkRetryResponseDto;
import com.sg.webhookservice.presentation.dto.MessageDto;
import com.sg.webhookservice.presentation.dto.MessageResponseDto;
import com.sg.webhookservice.presentation.dto.WebhookRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;

/**
 * Interfaz para el servicio de gestión de mensajes.
 * Define operaciones para enviar, procesar y consultar mensajes de webhook.
 */
public interface MessageService {

    /**
     * Recibe una solicitud de webhook y crea un mensaje para procesamiento.
     *
     * @param webhookName Nombre del webhook a usar
     * @param requestDto Payload del webhook
     * @return Respuesta con ID del mensaje creado
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe el webhook
     * @throws com.sg.webhookservice.presentation.exception.WebhookProcessingException si hay error al procesar
     */
    MessageResponseDto receiveWebhook(String webhookName, WebhookRequestDto requestDto);

    /**
     * Obtiene el estado de un mensaje.
     *
     * @param messageId ID del mensaje
     * @return Información del mensaje
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    MessageDto getMessageStatus(UUID messageId);

    /**
     * Procesa un mensaje (lo entrega a su destino).
     *
     * @param messageId ID del mensaje a procesar
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     * @throws com.sg.webhookservice.presentation.exception.WebhookProcessingException si hay error al procesar
     */
    void processMessage(UUID messageId);

    /**
     * Procesa un reintento de mensaje fallido.
     *
     * @param messageId ID del mensaje a reintentar
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     * @throws com.sg.webhookservice.presentation.exception.WebhookProcessingException si hay error al procesar
     */
    void processRetry(UUID messageId);

    /**
     * Marca un mensaje como cancelado.
     *
     * @param messageId ID del mensaje a cancelar
     * @throws com.sg.webhookservice.presentation.exception.ResourceNotFoundException si no existe
     */
    void cancelMessage(UUID messageId);

    /**
     * Ejecuta reintentos masivos para mensajes fallidos.
     *
     * @param request Parámetros para la operación de reintento
     * @return Resultado de la operación
     */
    BulkRetryResponseDto bulkRetry(BulkRetryRequestDto request);

    /**
     * Busca mensajes con criterios específicos.
     *
     * @param webhookName Nombre del webhook (opcional)
     * @param status Estado de los mensajes (opcional)
     * @param fromDate Fecha desde (opcional)
     * @param toDate Fecha hasta (opcional)
     * @param pageable Configuración de paginación
     * @return Página con los resultados
     */
    Page<MessageDto> searchMessages(
            String webhookName,
            String status,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            Pageable pageable);

    /**
     * Obtiene estadísticas resumidas sobre los mensajes.
     *
     * @param webhookName Nombre del webhook (opcional, para filtrar)
     * @param timeRange Rango de tiempo en horas (por defecto 24)
     * @return Mapa con estadísticas
     */
    Map<String, Object> getMessageStatistics(String webhookName, Integer timeRange);

    /**
     * Comprueba y procesa los mensajes pendientes en la base de datos.
     *
     * @return Número total de mensajes procesados
     */
    int processPendingMessages();
}