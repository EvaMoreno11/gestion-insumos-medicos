package com.evamoreno.inventario;

import org.junit.jupiter.api.Test;

/**
 * Comprueba que la aplicación completa arranca contra un PostgreSQL real
 * (Testcontainers) y que las migraciones de Flyway se aplican sin errores.
 */
class InventarioApplicationTests extends IntegracionTest {

    @Test
    void contextLoads() {
    }
}
