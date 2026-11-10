package com.evamoreno.inventario.alerta;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.evamoreno.inventario.IntegracionTest;

/** "Hoy" es el 15/06/2026 (reloj fijo de los tests). Los vencimientos se guardan como fin de mes. */
class AlertaControllerTest extends IntegracionTest {

    @Autowired
    private Clock clock;

    private LocalDate hoy;

    @BeforeEach
    void fijarHoy() {
        hoy = LocalDate.now(clock);
    }

    private Long producto(String nombre, int stockMinimo) throws Exception {
        return crear("/api/productos", """
                {"nombre": "%s", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "stockMinimo": %d}""".formatted(nombre, idCategoria("Otros"), stockMinimo));
    }

    private void entrada(Long productoId, String lote, LocalDate vence, int cantidad) throws Exception {
        mockMvc.perform(post("/api/movimientos/entrada").contentType(MediaType.APPLICATION_JSON).content("""
                {"productoId": %d, "numeroLote": "%s", "fechaVencimiento": %s, "cantidad": %d}"""
                .formatted(productoId, lote, vence == null ? "null" : "\"" + vence + "\"", cantidad)))
                .andExpect(status().isCreated());
    }

    @Test
    void stockBajoIncluyeAgotadosYBajosPrimeroLosAgotados() throws Exception {
        Long normal = producto("Producto normal", 5);
        Long bajo = producto("Producto bajo", 5);
        producto("Producto nunca ingresado", 0);
        Long soloVencido = producto("Producto solo vencido", 2);

        entrada(normal, "N1", null, 10);
        entrada(bajo, "B1", hoy.plusMonths(3), 3);
        entrada(soloVencido, "V1", hoy.minusMonths(1), 10); // vencido no cuenta: queda agotado

        mockMvc.perform(get("/api/alertas/stock-bajo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains(
                        "Producto nunca ingresado", "Producto solo vencido", "Producto bajo")))
                .andExpect(jsonPath("$[0].estadoStock").value("AGOTADO"))
                .andExpect(jsonPath("$[2].estadoStock").value("BAJO"))
                .andExpect(jsonPath("$[2].stockDisponible").value(3));
    }

    @Test
    void porVencerSoloIncluyeLotesDentroDeLaVentana() throws Exception {
        Long p = producto("Gasa", 0);
        entrada(p, "ESTE-MES", hoy, 1);                    // vence 30/06: faltan 15 días
        entrada(p, "PROXIMO-MES", hoy.plusMonths(1), 2);   // vence 31/07: faltan 46 días
        entrada(p, "EN-6-MESES", hoy.plusMonths(6), 3);
        entrada(p, "VENCIDO", hoy.minusMonths(1), 4);      // venció el 31/05

        mockMvc.perform(get("/api/alertas/por-vencer").param("dias", "30"))
                .andExpect(jsonPath("$[*].numeroLote", contains("ESTE-MES")))
                .andExpect(jsonPath("$[0].fechaVencimiento").value("2026-06-30"))
                .andExpect(jsonPath("$[0].diasParaVencer").value(15))
                .andExpect(jsonPath("$[0].productoNombre").value("Gasa"));

        mockMvc.perform(get("/api/alertas/por-vencer").param("dias", "60"))
                .andExpect(jsonPath("$[*].numeroLote", contains("ESTE-MES", "PROXIMO-MES")));

        mockMvc.perform(get("/api/alertas/vencidos"))
                .andExpect(jsonPath("$[*].numeroLote", contains("VENCIDO")))
                .andExpect(jsonPath("$[0].diasParaVencer").value(-15));
    }

    @Test
    void validaLaVentanaDeDias() throws Exception {
        mockMvc.perform(get("/api/alertas/por-vencer").param("dias", "0"))
                .andExpect(status().isBadRequest());
    }
}
