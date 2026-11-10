package com.evamoreno.inventario;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Configuración de los tests de integración:
 * un PostgreSQL temporal en Docker y un reloj detenido en una fecha fija.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** "Hoy" en los tests: 15 de junio de 2026 (hora de Perú). */
    public static final Instant AHORA = Instant.parse("2026-06-15T17:00:00Z");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    /**
     * Reloj fijo: los vencimientos se calculan por mes, y con una fecha fija los tests dan el mismo
     * resultado cualquier día en que se ejecuten (también el 1 o el 31).
     */
    @Bean
    @Primary
    Clock relojFijo() {
        return Clock.fixed(AHORA, ZoneId.of("America/Lima"));
    }
}
