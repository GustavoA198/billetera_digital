package com.billetera_digital.exception;

import com.billetera_digital.exception.dto.FieldErrorDetail;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Maneja las excepciones del negocio(BusinessException).
     *
     * @param ex      La excepción lanzada.
     * @return Una respuesta con detalles del error.
     */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
        problemDetail.setProperty("code", ex.getCode());
        problemDetail.setProperty("traceId", MDC.get("traceId"));
        return problemDetail;
    }

    /**
     * Maneja las excepciones de violación de integridad de datos (DataIntegrityViolationException).
     * La que se ´produce cuando se intenta insertar un registro con un valor duplicado en una columna que tiene una restricción de unicidad.
     * Esto en el caso de que se intente el registro en el mismo instante tanto asi que la validacion del servicio no alcance a detectar el duplicado.
     *
     * @param ex      La excepción lanzada.
     * @return Una respuesta con detalles del error.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    protected  ProblemDetail dataIntegrityViolationException(DataIntegrityViolationException ex){
        if (ex.getMostSpecificCause() instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
            log.error("Data integrity violation exception", ex);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Data integrity violation exception");
            problemDetail.setProperty("code", "DATA_INTEGRITY_VIOLATION_EXCEPTION");
            problemDetail.setProperty("traceId", MDC.get("traceId"));
            return problemDetail;
        }
        return handleUnexpected(ex);
    }


    /**
     * Maneja las excepciones de autenticación(AuthenticationException).
     *
     * @param ex      La excepción lanzada.
     * @return Una respuesta con detalles del error.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication exception", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Se requiere autenticación");
        problemDetail.setProperty("code", "UNAUTHORIZED");
        problemDetail.setProperty("traceId", MDC.get("traceId"));
        return problemDetail;
    }

    /**
     * Maneja las excepciones de acceso denegado (AccessDeniedException).
     *
     * @param ex      La excepción lanzada.
     * @return Una respuesta con detalles del error.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied exception", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación");
        problemDetail.setProperty("code", "FORBIDDEN");
        problemDetail.setProperty("traceId", MDC.get("traceId"));
        return problemDetail;
    }

    /**
     * Maneja las excepciones inesperadas del sistema(INTERNAL_ERROR).
     *
     * @param ex      La excepción lanzada.
     * @return Una respuesta con detalles del error.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");
        problemDetail.setProperty("code", "INTERNAL_ERROR");
        problemDetail.setProperty("traceId", MDC.get("traceId"));
        return problemDetail;
    }

    /**
     * Maneja las excepciones de validación de argumentos(VALIDATION_ERROR).
     *
     * @param ex      La excepción lanzada.
     * @return Una respuesta con detalles del error.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail problemDetail = ex.getBody();
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        List<FieldErrorDetail> errorMessages = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        problemDetail.setProperty("errors", errorMessages);

        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }


    /**
     * Completa las respuestas de error de Spring con el código de error y el identificador de traza.
     * Todas aquellas que no se estan manejando puntualmente como por ejemplo handleMethodArgumentNotValid
     *
     * @param ex         La excepción lanzada.
     * @param body       El cuerpo propuesto por el manejador.
     * @param headers    Las cabeceras de la respuesta.
     * @param statusCode El código de estado HTTP.
     * @param request    La petición web que originó la excepción.
     * @return Una respuesta con los detalles del error.
     */

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
            problemDetail.setProperty("traceId", MDC.get("traceId"));
            if (!problemDetail.getProperties().containsKey("code")) {
                problemDetail.setProperty("code", HttpStatus.valueOf(problemDetail.getStatus()).name());
            }
        }
        return response;
    }
}
