package com.evamoreno.inventario.pedido;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

/**
 * Costo y ganancia de cada pedido (uso interno) y el precio sugerido al agregar un producto en una corrección.
 * "Hoy" es el 15/06/2026 (reloj fijo de los tests).
 */
class CostoDelPedidoTest extends IntegracionTest {

    private static final String URL = "/api/pedidos";
    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);

    private Long jeringa;
    private Long botica;

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static Long id(ResultActions resultado, String ruta) throws Exception {
        return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta)).longValue();
    }

    private void entrada(String lote, LocalDate vence, int cantidad, String costo) throws Exception {
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "%s", "fechaVencimiento": "%s", "cantidad": %d, "costo": %s}"""
                .formatted(jeringa, lote, vence, cantidad, costo)).andExpect(status().isCreated());
    }

    /** Jeringa con dos lotes: 3 a S/ 8 (vence antes) y 10 a S/ 10; el producto queda con costo S/ 10. */
    @BeforeEach
    void datos() throws Exception {
        jeringa = crear("/api/productos", """
                {"nombre": "Jeringa 5 ml", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja"}""".formatted(idCategoria("Otros")));
        entrada("L-A", HOY.plusMonths(6), 3, "8");
        entrada("L-B", HOY.plusMonths(18), 10, "10");
        botica = crear("/api/clientes", """
                {"nombre": "Botica Demo", "porcentaje": 15}""");
    }

    @Test
    void unBorradorEstimaElCostoConElCostoActualYNoCuentaLoQueNoTieneCosto() throws Exception {
        Long pedido = crear(URL, """
                {"clienteId": %d}""".formatted(botica));
        postJson(URL + "/" + pedido + "/items", """
                {"productoId": %d, "cantidad": 4}""".formatted(jeringa))            // 4 × 11.50 = 46
                .andExpect(jsonPath("$.costo.costo").value(40.0))                    // 4 × 10
                .andExpect(jsonPath("$.costo.ganancia").value(6.0))
                .andExpect(jsonPath("$.costo.itemsSinCosto").value(0));
        postJson(URL + "/" + pedido + "/items", """
                {"descripcion": "Tensiómetro digital", "cantidad": 1, "precio": 40}""")
                .andExpect(jsonPath("$.costo.costo").value(40.0))
                .andExpect(jsonPath("$.costo.itemsSinCosto").value(1));

        mockMvc.perform(get(URL).param("estado", "BORRADOR"))
                .andExpect(jsonPath("$.contenido[0].costo.costo").value(40.0))
                .andExpect(jsonPath("$.contenido[0].costo.ganancia").value(6.0))
                .andExpect(jsonPath("$.contenido[0].costo.itemsSinCosto").value(1));
    }

    @Test
    void unDespachadoUsaElCostoRealDeLosLotesYSeAjustaConLasCorrecciones() throws Exception {
        Long pedido = crear(URL, """
                {"clienteId": %d}""".formatted(botica));
        postJson(URL + "/" + pedido + "/items", """
                {"productoId": %d, "cantidad": 5}""".formatted(jeringa));           // 5 × 11.50 = 57.50
        Long item = id(postJson(URL + "/" + pedido + "/despachar", "")
                .andExpect(jsonPath("$.costo.costo").value(44.0))                    // 3 × 8 + 2 × 10
                .andExpect(jsonPath("$.costo.ganancia").value(13.5)), "$.items[0].id");

        mockMvc.perform(get(URL + "/despachados"))
                .andExpect(jsonPath("$.lista.contenido[0].costo.costo").value(44.0))
                .andExpect(jsonPath("$.lista.contenido[0].costo.ganancia").value(13.5));

        // Devuelven 1 del lote L-B: el costo baja S/ 10 y la venta S/ 11.50
        Long loteB = id(mockMvc.perform(get(URL + "/{id}", pedido)), "$.items[0].lotes[1].loteId");
        postJson(URL + "/" + pedido + "/correcciones", """
                {"cambios": [{"itemId": %d, "cantidad": 4, "lotes": [{"loteId": %d, "cantidad": 1}]}]}"""
                .formatted(item, loteB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costo.costo").value(34.0))
                .andExpect(jsonPath("$.costo.ganancia").value(12.0));
    }

    @Test
    void siSeVendePorDebajoDelCostoLaGananciaSaleNegativa() throws Exception {
        Long pedido = crear(URL, """
                {"clienteId": %d}""".formatted(botica));
        Long item = id(postJson(URL + "/" + pedido + "/items", """
                {"productoId": %d, "cantidad": 2}""".formatted(jeringa)), "$.items[0].id");
        putJson(URL + "/" + pedido + "/items/" + item, """
                {"cantidad": 2, "precio": 9}""")
                .andExpect(jsonPath("$.costo.ganancia").value(-2.0));
    }

    @Test
    void unPedidoAnuladoNoMuestraCosto() throws Exception {
        Long pedido = crear(URL, """
                {"clienteId": %d}""".formatted(botica));
        postJson(URL + "/" + pedido + "/anular", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.costo").doesNotExist());
    }

    @Test
    void elPrecioSugeridoEsElEspecialDelClienteOElCalculado() throws Exception {
        Long anterior = crear(URL, """
                {"clienteId": %d}""".formatted(botica));
        Long item = id(postJson(URL + "/" + anterior + "/items", """
                {"productoId": %d, "cantidad": 1}""".formatted(jeringa)), "$.items[0].id");
        Long pedido = crear(URL, """
                {"clienteId": %d}""".formatted(botica));

        mockMvc.perform(get(URL + "/{id}/precio-sugerido", pedido).param("productoId", jeringa.toString()))
                .andExpect(jsonPath("$.precio").value(11.5))
                .andExpect(jsonPath("$.especial").value(false));
        putJson(URL + "/" + anterior + "/items/" + item, """
                {"cantidad": 1, "precio": 12.80}""").andExpect(status().isOk());
        mockMvc.perform(get(URL + "/{id}/precio-sugerido", pedido).param("productoId", jeringa.toString()))
                .andExpect(jsonPath("$.precio").value(12.8))
                .andExpect(jsonPath("$.especial").value(true));
        mockMvc.perform(get(URL + "/{id}/precio-sugerido", pedido).param("productoId", "999999"))
                .andExpect(status().isNotFound());
    }
}
