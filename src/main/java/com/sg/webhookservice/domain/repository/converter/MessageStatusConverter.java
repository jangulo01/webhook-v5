package com.sg.webhookservice.domain.repository.converter;

import com.sg.webhookservice.domain.entity.Message;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Convertidor personalizado para Message.MessageStatus.
 * Permite que Spring Data JPA maneje correctamente la conversión entre String y MessageStatus.
 */
@Component
public class MessageStatusConverter implements Converter<String, Message.MessageStatus> {

    @Override
    public Message.MessageStatus convert(String source) {
        try {
            return Message.MessageStatus.valueOf(source);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}