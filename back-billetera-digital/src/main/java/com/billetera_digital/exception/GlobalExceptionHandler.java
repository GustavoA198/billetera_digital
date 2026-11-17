package com.billetera_digital.exception;

import com.billetera_digital.exception.dto.FieldErrorDetail;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Maneja las excepciones del negocio.
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
     * Maneja las excepciones inesperadas del sistema.
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
     * Maneja las excepciones de validación de argumentos.
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
