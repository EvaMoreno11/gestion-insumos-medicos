package com.evamoreno.inventario.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación en la zona horaria del negocio (Perú).
 * "Hoy" se usa para saber qué lotes están vencidos; inyectar un Clock en lugar de llamar
 * a LocalDate.now() permite que los tests controlen la fecha.
 */
@Configuration
public class TiempoConfig {

    @Bean
    Clock clock(@Value("${app.zona-horaria:America/Lima}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
