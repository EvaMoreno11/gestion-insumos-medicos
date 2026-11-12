package com.evamoreno.inventario.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.evamoreno.inventario.seguridad.AuthService;

/**
 * Convierte las excepciones en respuestas de error con el formato estándar
 * "Problem Details" (RFC 9457), por ejemplo:
 *
 * <pre>
 * { "status": 409, "title": "Conflicto", "detail": "Ya existe un producto con el código JER-00001" }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    ProblemDetail solicitudInvalida(SolicitudInvalidaException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud inválida", ex.getMessage());
    }

    @ExceptionHandler(AuthService.CredencialesInvalidasException.class)
    ProblemDetail credencialesInvalidas(AuthService.CredencialesInvalidasException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "No autorizado", ex.getMessage());
    }

    @ExceptionHandler(AuthService.CuentaBloqueadaException.class)
    ProblemDetail cuentaBloqueada(AuthService.CuentaBloqueadaException ex) {
        return problema(HttpStatus.TOO_MANY_REQUESTS, "Cuenta bloqueada temporalmente", ex.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    ProblemDetail conflicto(ConflictoException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    /** Respaldo: la base de datos rechazó un duplicado que el servicio no detectó antes. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos", ex);
        return problema(HttpStatus.CONFLICT, "Conflicto",
                "La operación entra en conflicto con datos existentes (posible duplicado).");
    }

    /** Errores de validación (@Valid): devuelve qué campo falló y por qué. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Revisa los campos marcados.");
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
