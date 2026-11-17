package com.billetera_digital.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    /**
     * Este filtro se ejecuta una vez por cada petición HTTP y genera un identificador único (UUID) para cada solicitud.
     * El UUID se almacena en el MDC (Mapped Diagnostic Context) con la clave "traceId", lo que permite rastrear la solicitud
     * a través de los logs. Al finalizar la solicitud, el UUID se elimina del MDC.
     *
     * @param request  La solicitud HTTP entrante.
     * @param response La respuesta HTTP saliente.
     * @param filterChain El objeto FilterChain que permite pasar la solicitud al siguiente filtro en la cadena.
     * @throws ServletException Si ocurre un error durante el procesamiento del filtro.
     * @throws IOException      Si ocurre un error de entrada/salida durante el procesamiento del filtro.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        MDC.put("traceId", UUID.randomUUID().toString());   // 1. generar un UUID y guardarlo en el MDC con la llave "traceId"
        try {
            filterChain.doFilter(request, response);   // deja seguir la petición
        } finally {
            MDC.remove("traceId");  // 2. borrar la llave del MDC
        }
    }
}
