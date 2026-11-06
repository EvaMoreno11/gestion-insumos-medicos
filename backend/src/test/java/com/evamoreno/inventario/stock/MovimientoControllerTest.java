package com.evamoreno.inventario.stock;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

class MovimientoControllerTest extends IntegracionTest {

    @Autowired
    private Clock clock;

    private LocalDate hoy;
    private Long productoId;

    @BeforeEach
    void crearProducto() throws Exception {
        hoy = LocalDate.now(clock);
        productoId = crear("/api/productos", """
                {"nombre": "Jeringa descartable 5 ml", "marca": "Marca A", "categoriaId": %d,
                 "presentacion": "Caja x 100", "unidadMedida": "Caja", "stockMinimo": 5}"""
                .formatted(idCategoria("Jeringas")));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions entrada(String lote, LocalDate vence, int cantidad) throws Exception {
        return postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": %s, "fechaVencimiento": %s, "cantidad": %d}"""
                .formatted(productoId, lote == null ? "null" : "\"" + lote + "\"",
                        vence == null ? "null" : "\"" + vence + "\"", cantidad));
    }

    private ResultActions salida(int cantidad, Long loteId) throws Exception {
        return postJson("/api/movimientos/salida", """
                {"productoId": %d, "cantidad": %d, "loteId": %s, "nota": "Entrega"}"""
                .formatted(productoId, cantidad, loteId));
    }

    private Long idLote(ResultActions resultado) throws Exception {
        String json = resultado.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.movimientos[0].loteId")).longValue();
    }

    // ------------------------------------------------------------------ entradas

    @Test
    void unaEntradaCreaElLoteYOtraConElMismoNumeroLeSuma() throws Exception {
        entrada("l-100", hoy.plusDays(200), 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimientos[0].tipo").value("ENTRADA"))
                .andExpect(jsonPath("$.movimientos[0].numeroLote").value("L-100"))
                .andExpect(jsonPath("$.stockDisponible").value(2));

        // Mismo lote escrito con minúsculas y espacios
        entrada(" L-100 ", hoy.plusDays(200), 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimientos[0].stockResultanteLote").value(5))
                .andExpect(jsonPath("$.stockDisponible").value(5));

        mockMvc.perform(get("/api/productos/{id}", productoId))
                .andExpect(jsonPath("$.lotes.length()").value(1))
                .andExpect(jsonPath("$.lotes[0].cantidad").value(5))
                .andExpect(jsonPath("$.estadoStock").value("BAJO"));
    }

    @Test
    void lasEntradasSinNumeroDeLoteVanAlMismoLote() throws Exception {
        entrada(null, null, 4).andExpect(status().isCreated());
        entrada(null, null, 6).andExpect(status().isCreated());

        mockMvc.perform(get("/api/productos/{id}", productoId))
                .andExpect(jsonPath("$.lotes.length()").value(1))
                .andExpect(jsonPath("$.stockDisponible").value(10))
                .andExpect(jsonPath("$.estadoStock").value("NORMAL"));
    }

    @Test
    void rechazaElMismoLoteConOtraFechaDeVencimiento() throws Exception {
        entrada("L-1", hoy.plusDays(100), 2);

        entrada("L-1", hoy.plusDays(150), 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("ya está registrado con vencimiento")));
    }

    @Test
    void validaLaCantidad() throws Exception {
        entrada("L-1", null, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cantidad").value("Debe ser mayor que 0"));
    }

    @Test
    void noRegistraMovimientosDeUnProductoDesactivado() throws Exception {
        mockMvc.perform(patch("/api/productos/{id}/desactivar", productoId));

        entrada("L-1", null, 5).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ salidas FEFO

    @Test
    void laSalidaUsaPrimeroElLoteQueVenceAntesYNuncaUnoVencido() throws Exception {
        entrada("A", hoy.plusDays(100), 4);
        entrada("B", hoy.plusDays(30), 3);
        entrada("C", null, 10);                    // sin vencimiento: va al final
        Long vencido = idLote(entrada("D", hoy.minusDays(1), 5)); // vencido: nunca se despacha

        mockMvc.perform(get("/api/productos/{id}", productoId))
                .andExpect(jsonPath("$.stockDisponible").value(17))
                .andExpect(jsonPath("$.stockVencido").value(5))
                .andExpect(jsonPath("$.lotes[0].numeroLote").value("D"))
                .andExpect(jsonPath("$.lotes[0].vencido").value(true));

        salida(8, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimientos.length()").value(3))
                .andExpect(jsonPath("$.movimientos[0].numeroLote").value("B"))
                .andExpect(jsonPath("$.movimientos[0].cantidad").value(3))
                .andExpect(jsonPath("$.movimientos[1].numeroLote").value("A"))
                .andExpect(jsonPath("$.movimientos[1].cantidad").value(4))
                .andExpect(jsonPath("$.movimientos[2].numeroLote").value("C"))
                .andExpect(jsonPath("$.movimientos[2].cantidad").value(1))
                .andExpect(jsonPath("$.stockDisponible").value(9));

        salida(1, vencido)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("no se puede despachar")));
    }

    @Test
    void siNoAlcanzaElStockNoSacaNada() throws Exception {
        entrada("A", hoy.plusDays(100), 4);
        entrada("B", hoy.minusDays(10), 50); // vencido: no cuenta

        salida(5, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Stock insuficiente de \"Jeringa descartable 5 ml\": se pidieron 5 y hay 4 disponibles"));

        mockMvc.perform(get("/api/productos/{id}", productoId))
                .andExpect(jsonPath("$.stockDisponible").value(4));
    }

    @Test
    void permiteSacarDeUnLoteEspecifico() throws Exception {
        entrada("A", hoy.plusDays(30), 4);
        Long loteB = idLote(entrada("B", hoy.plusDays(300), 10));

        salida(6, loteB)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimientos.length()").value(1))
                .andExpect(jsonPath("$.movimientos[0].numeroLote").value("B"))
                .andExpect(jsonPath("$.stockDisponible").value(8));

        salida(5, loteB)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("en ese lote")));
    }

    // ------------------------------------------------------------------ ajustes e historial

    @Test
    void unAjusteDejaElLoteConLaCantidadRealYQuedaEnElHistorial() throws Exception {
        Long vencido = idLote(entrada("D", hoy.minusDays(3), 5));
        entrada("A", hoy.plusDays(90), 10);
        salida(2, null);

        postJson("/api/movimientos/ajuste", """
                {"loteId": %d, "cantidadReal": 0, "nota": "Baja por vencimiento"}""".formatted(vencido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimientos[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.movimientos[0].cantidad").value(-5))
                .andExpect(jsonPath("$.movimientos[0].stockResultanteLote").value(0));

        mockMvc.perform(get("/api/productos/{id}", productoId))
                .andExpect(jsonPath("$.stockVencido").value(0))
                .andExpect(jsonPath("$.lotes.length()").value(1));

        // Más reciente primero
        mockMvc.perform(get("/api/movimientos").param("productoId", productoId.toString()))
                .andExpect(jsonPath("$.totalElementos").value(4))
                .andExpect(jsonPath("$.contenido[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.contenido[1].tipo").value("SALIDA"))
                .andExpect(jsonPath("$.contenido[3].tipo").value("ENTRADA"));
    }

    @Test
    void unAjusteSinCambioOSinMotivoSeRechaza() throws Exception {
        Long lote = idLote(entrada("A", null, 5));

        postJson("/api/movimientos/ajuste", """
                {"loteId": %d, "cantidadReal": 5, "nota": "Conteo"}""".formatted(lote))
                .andExpect(status().isConflict());
        postJson("/api/movimientos/ajuste", """
                {"loteId": %d, "cantidadReal": 3, "nota": ""}""".formatted(lote))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nota").value("Indica el motivo del ajuste"));
    }

    @Test
    void elBuscadorMuestraElStockDisponible() throws Exception {
        entrada("A", hoy.plusDays(60), 7);
        entrada("B", hoy.minusDays(1), 100);

        mockMvc.perform(get("/api/productos").param("buscar", "jeringa 5"))
                .andExpect(jsonPath("$.contenido[0].stockDisponible").value(7))
                .andExpect(jsonPath("$.contenido[0].estadoStock").value("NORMAL"));
    }
}
