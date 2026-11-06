package com.evamoreno.inventario;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Comprueba que la aplicación completa arranca contra un PostgreSQL real
 * (Testcontainers) y que las migraciones de Flyway se aplican sin errores.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InventarioApplicationTests {

    @Test
    void contextLoads() {
    }
}
