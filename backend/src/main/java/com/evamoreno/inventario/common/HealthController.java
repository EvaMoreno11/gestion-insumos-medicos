package com.evamoreno.inventario.common;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint simple para comprobar que la API está en línea.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    public record EstadoResponse(String estado, String servicio, Instant hora) {
    }

    @GetMapping
    public EstadoResponse health() {
        return new EstadoResponse("OK", "inventario", Instant.now());
    }
}
