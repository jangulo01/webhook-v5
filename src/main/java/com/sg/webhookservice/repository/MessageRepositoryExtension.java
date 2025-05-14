package com.sg.webhookservice.repository;

import com.sg.webhookservice.model.Message.MessageStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extensión del repositorio de Message con métodos específicos necesarios para la implementación.
 */
@Repository
public interface MessageRepositoryExtension {

    /**
     * Count messages grouped by status
     *
     * @return Map with counts by status
     */
    @Query("SELECT m.status, COUNT(m) FROM Message m GROUP BY m.status")
    List<Object[]> countByStatusGroupedRaw();

    /**
     * Implementación del método para contar mensajes por estado.
     * Este es un método de conveniencia para convertir los resultados de la consulta
     * a un formato más fácil de usar.
     *
     * @return Map with status as key and count as value
     */
    default Map<MessageStatus, Long> countByStatusGrouped() {
        List<Object[]> results = countByStatusGroupedRaw();
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (MessageStatus) row[0],
                        row -> (Long) row[1]
                ));
    }
}
