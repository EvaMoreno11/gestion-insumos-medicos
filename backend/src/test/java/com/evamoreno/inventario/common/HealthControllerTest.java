package com.evamoreno.inventario.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.evamoreno.inventario.IntegracionTest;

class HealthControllerTest extends IntegracionTest {

    @Test
    void devuelveEstadoOkSinNecesitarSesion() throws Exception {
        // El servicio de hosting consulta esta ruta para saber si la app está en línea
        mockMvcSinSesion.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OK"))
                .andExpect(jsonPath("$.servicio").value("inventario"))
                .andExpect(jsonPath("$.hora").exists());
    }
}
