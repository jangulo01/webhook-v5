package com.sg.webhookservice.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

/**
 * Excepción lanzada cuando ocurre un error en operaciones de base de datos.
 * 
 * Esta excepción encapsula problemas como:
 * - Errores de conexión a la base de datos
 * - Problemas de integridad de datos
 * - Errores en consultas SQL
 * - Problemas de concurrencia y bloqueo
 * - Problemas con transacciones
 */
public class DatabaseOperationException extends ApiException {
    
    /**
     * Código de error estándar para esta excepción
     */
    private static final String ERROR_CODE = "DATABASE_OPERATION_ERROR";
    
    /**
     * Estado HTTP predeterminado para esta excepción
     */
    private static final HttpStatus DEFAULT_STATUS = HttpStatus.INTERNAL_SERVER_ERROR;
    
    /**
     * Tipo de operación de base de datos que causó el error
     */
    private final OperationType operationType;
    
    /**
     * Constructor básico con mensaje de error.
     * 
     * @param message Mensaje descriptivo del error
     */
    public DatabaseOperationException(String message) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.operationType = OperationType.UNKNOWN;
    }
    
    /**
     * Constructor con mensaje y tipo de operación.
     * 
     * @param message Mensaje descriptivo del error
     * @param operationType Tipo de operación que causó el error
     */
    public DatabaseOperationException(String message, OperationType operationType) {
        super(message, DEFAULT_STATUS, ERROR_CODE);
        this.operationType = operationType;
    }
    
    /**
     * Constructor con mensaje, tipo de operación e información adicional.
     * 
     * @param message Mensaje descriptivo del error
     * @param operationType Tipo de operación que causó el error
     * @param additionalInfo Información adicional sobre el error
     */
    public DatabaseOperationException(String message, OperationType operationType, String additionalInfo) {
        super(message, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.operationType = operationType;
    }
    
    /**
     * Constructor para encapsular excepciones de Spring Data.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción original de acceso a datos
     * @param operationType Tipo de operación que causó el error
     */
    public DatabaseOperationException(String message, DataAccessException cause, OperationType operationType) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE);
        this.operationType = operationType;
    }
    
    /**
     * Constructor completo con todos los parámetros.
     * 
     * @param message Mensaje descriptivo del error
     * @param cause Excepción que causó este error
     * @param operationType Tipo de operación que causó el error
     * @param additionalInfo Información adicional sobre el error
     */
    public DatabaseOperationException(String message, Throwable cause, OperationType operationType, String additionalInfo) {
        super(message, cause, DEFAULT_STATUS, ERROR_CODE, additionalInfo);
        this.operationType = operationType;
    }
    
    /**
     * Obtiene el tipo de operación que causó el error.
     * 
     * @return Tipo de operación de base de datos
     */
    public OperationType getOperationType() {
        return operationType;
    }
    
    /**
     * Crea una instancia específica para errores de conexión.
     * 
     * @param dbName Nombre de la base de datos
     * @param cause Excepción original
     * @return Instancia de DatabaseOperationException
     */
    public static DatabaseOperationException connectionError(String dbName, Throwable cause) {
        return new DatabaseOperationException(
            "Error connecting to database",
            cause,
            OperationType.CONNECTION,
            "Database: " + dbName
        );
    }
    
    /**
     * Crea una instancia específica para errores de consulta.
     * 
     * @param entity Entidad relacionada con la consulta
     * @param cause Excepción original
     * @return Instancia de DatabaseOperationException
     */
    public static DatabaseOperationException queryError(String entity, Throwable cause) {
        return new DatabaseOperationException(
            "Error executing database query",
            cause,
            OperationType.QUERY,
            "Entity: " + entity
        );
    }
    
    /**
     * Crea una instancia específica para errores de actualización.
     * 
     * @param entity Entidad que se intentaba actualizar
     * @param id Identificador de la entidad
     * @param cause Excepción original
     * @return Instancia de DatabaseOperationException
     */
    public static DatabaseOperationException updateError(String entity, String id, Throwable cause) {
        return new DatabaseOperationException(
            "Error updating database record",
            cause,
            OperationType.UPDATE,
            String.format("Entity: %s, ID: %s", entity, id)
        );
    }
    
    /**
     * Crea una instancia específica para errores de inserción.
     * 
     * @param entity Entidad que se intentaba insertar
     * @param cause Excepción original
     * @return Instancia de DatabaseOperationException
     */
    public static DatabaseOperationException insertError(String entity, Throwable cause) {
        return new DatabaseOperationException(
            "Error inserting new database record",
            cause,
            OperationType.INSERT,
            "Entity: " + entity
        );
    }
    
    /**
     * Crea una instancia específica para errores de eliminación.
     * 
     * @param entity Entidad que se intentaba eliminar
     * @param id Identificador de la entidad
     * @param cause Excepción original
     * @return Instancia de DatabaseOperationException
     */
    public static DatabaseOperationException deleteError(String entity, String id, Throwable cause) {
        return new DatabaseOperationException(
            "Error deleting database record",
            cause,
            OperationType.DELETE,
            String.format("Entity: %s, ID: %s", entity, id)
        );
    }
    
    /**
     * Crea una instancia específica para errores de transacción.
     * 
     * @param transactionId Identificador de la transacción (si está disponible)
     * @param cause Excepción original
     * @return Instancia de DatabaseOperationException
     */
    public static DatabaseOperationException transactionError(String transactionId, Throwable cause) {
        return new DatabaseOperationException(
            "Error in database transaction",
            cause,
            OperationType.TRANSACTION,
            transactionId != null ? "Transaction ID: " + transactionId : null
        );
    }
    
    /**
     * Tipos de operación de base de datos que pueden causar errores.
     */
    public enum OperationType {
        /**
         * Error al establecer conexión con la base de datos
         */
        CONNECTION,
        
        /**
         * Error al ejecutar una consulta de lectura
         */
        QUERY,
        
        /**
         * Error al actualizar registros existentes
         */
        UPDATE,
        
        /**
         * Error al insertar nuevos registros
         */
        INSERT,
        
        /**
         * Error al eliminar registros
         */
        DELETE,
        
        /**
         * Error relacionado con transacciones
         */
        TRANSACTION,
        
        /**
         * Error de bloqueo o concurrencia
         */
        LOCKING,
        
        /**
         * Error de migración o esquema
         */
        SCHEMA,
        
        /**
         * Tipo de error desconocido o no especificado
         */
        UNKNOWN
    }
}