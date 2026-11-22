package com.evamoreno.inventario.pedido;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;

/** Productos agregados a mano a "Por comprar" y cómo se juntan con lo que falta para los pedidos pendientes. */
class PorComprarAManoTest extends IntegracionTest {

    private static final String LISTA = "/api/pedidos/por-comprar";
    private static final String A_MANO = LISTA + "/a-mano/";

    private Long guantes;
    private Long alcohol;

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions agregarAMano(Long producto, String json) throws Exception {
        return mockMvc.perform(put(A_MANO + producto).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void entrada(Long producto, String lote, int cantidad, String costo) throws Exception {
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "%s", "cantidad": %d, "costo": %s}"""
                .formatted(producto, lote, cantidad, costo)).andExpect(status().isCreated());
    }

    @BeforeEach
    void datos() throws Exception {
        guantes = crear("/api/productos", """
                {"nombre": "Guante de nitrilo talla M", "marca": "Marca A", "categoriaId": %d,
                 "presentacion": "Caja x 100", "unidadMedida": "Caja", "stockMinimo": 10}"""
                .formatted(idCategoria("Guantes")));
        alcohol = crear("/api/productos", """
                {"nombre": "Alcohol 70° frasco 1 L", "marca": "Marca D", "categoriaId": %d,
                 "presentacion": "Frasco 1 L", "unidadMedida": "Frasco", "stockMinimo": 30}"""
                .formatted(idCategoria("Otros")));
        entrada(guantes, "G1", 3, "18");
        entrada(alcohol, "A1", 4, "9.80");
        // Un pedido pendiente pide 24 frascos y solo hay 4: faltan 20
        Long botica = crear("/api/clientes", """
                {"nombre": "Botica Santa Rosa", "porcentaje": 10}""");
        Long pedido = crear("/api/pedidos", """
                {"clienteId": %d}""".formatted(botica));
        postJson("/api/pedidos/" + pedido + "/items", """
                {"productoId": %d, "cantidad": 24}""".formatted(alcohol)).andExpect(status().isOk());
    }

    @Test
    void unProductoQueNingunPedidoEsperaSeAgregaConSuCantidadYNota() throws Exception {
        agregarAMano(guantes, """
                {"cantidad": 20, "nota": "  Urgente,  se acaba esta semana "}""").andExpect(status().isNoContent());

        mockMvc.perform(get(LISTA))
                .andExpect(jsonPath("$.productos[*].nombre", contains("Alcohol 70° frasco 1 L",
                        "Guante de nitrilo talla M")))
                .andExpect(jsonPath("$.productos[1].stockDisponible").value(3))
                .andExpect(jsonPath("$.productos[1].stockMinimo").value(10))
                .andExpect(jsonPath("$.productos[1].pedido").value(0))
                .andExpect(jsonPath("$.productos[1].faltante").value(0))
                .andExpect(jsonPath("$.productos[1].comprar").value(20))
                .andExpect(jsonPath("$.productos[1].pedidos", empty()))
                .andExpect(jsonPath("$.productos[1].aMano.cantidad").value(20))
                .andExpect(jsonPath("$.productos[1].aMano.nota").value("Urgente, se acaba esta semana"))
                .andExpect(jsonPath("$.productos[1].aMano.creadoEn").exists())
                // El alcohol sigue igual, solo por el pedido
                .andExpect(jsonPath("$.productos[0].comprar").value(20))
                .andExpect(jsonPath("$.productos[0].aMano").doesNotExist())
                .andExpect(jsonPath("$.costoTotal").value(556.0)); // 20 × 9.80 + 20 × 18
    }

    @Test
    void siYaFaltaParaUnPedidoSaleUnaSolaVezConLoAgregadoSumado() throws Exception {
        agregarAMano(alcohol, """
                {"cantidad": 10}""").andExpect(status().isNoContent());

        mockMvc.perform(get(LISTA))
                .andExpect(jsonPath("$.productos", hasSize(1)))
                .andExpect(jsonPath("$.productos[0].pedido").value(24))
                .andExpect(jsonPath("$.productos[0].faltante").value(20))
                .andExpect(jsonPath("$.productos[0].comprar").value(30))
                .andExpect(jsonPath("$.productos[0].pedidos[*].cliente", contains("Botica Santa Rosa")))
                .andExpect(jsonPath("$.productos[0].aMano.cantidad").value(10))
                .andExpect(jsonPath("$.productos[0].aMano.nota").doesNotExist())
                .andExpect(jsonPath("$.costoTotal").value(294.0)); // 30 × 9.80
    }

    @Test
    void sinCantidadQuedaComoRecordatorioYNoSeSumaAlCosto() throws Exception {
        agregarAMano(guantes, """
                {"nota": "Ver cuántas pedir"}""").andExpect(status().isNoContent());

        mockMvc.perform(get(LISTA))
                .andExpect(jsonPath("$.productos", hasSize(2)))
                .andExpect(jsonPath("$.productos[1].comprar").value(0))
                .andExpect(jsonPath("$.productos[1].aMano.cantidad").doesNotExist())
                .andExpect(jsonPath("$.productos[1].aMano.nota").value("Ver cuántas pedir"))
                .andExpect(jsonPath("$.costoTotal").value(196.0)) // solo el alcohol
                .andExpect(jsonPath("$.productosSinCosto").value(0));
    }

    @Test
    void agregarloDeNuevoCambiaLaCantidadYLaNotaSinRepetirlo() throws Exception {
        agregarAMano(guantes, """
                {"cantidad": 20, "nota": "Urgente"}""").andExpect(status().isNoContent());
        agregarAMano(guantes, """
                {"cantidad": 5, "nota": ""}""").andExpect(status().isNoContent());

        mockMvc.perform(get(LISTA))
                .andExpect(jsonPath("$.productos", hasSize(2)))
                .andExpect(jsonPath("$.productos[1].comprar").value(5))
                .andExpect(jsonPath("$.productos[1].aMano.nota").doesNotExist());
    }

    @Test
    void quitarBorraSoloLoAgregadoAManoYLoDelPedidoSigue() throws Exception {
        agregarAMano(alcohol, """
                {"cantidad": 10}""");
        agregarAMano(guantes, """
                {"cantidad": 20}""");
        mockMvc.perform(delete(A_MANO + alcohol)).andExpect(status().isNoContent());
        mockMvc.perform(delete(A_MANO + guantes)).andExpect(status().isNoContent());
        mockMvc.perform(delete(A_MANO + guantes)).andExpect(status().isNoContent()); // ya no estaba: no pasa nada

        mockMvc.perform(get(LISTA))
                .andExpect(jsonPath("$.productos", hasSize(1)))
                .andExpect(jsonPath("$.productos[0].comprar").value(20))
                .andExpect(jsonPath("$.productos[0].aMano").doesNotExist());
    }

    @Test
    void alRegistrarUnaEntradaDelProductoSeQuitaSolo() throws Exception {
        agregarAMano(guantes, """
                {"cantidad": 20}""");
        agregarAMano(alcohol, """
                {"cantidad": 10}""");
        entrada(guantes, "G2", 20, "18");
        entrada(alcohol, "A2", 5, "9.80"); // ahora hay 9 y el pedido pide 24: faltan 15

        mockMvc.perform(get(LISTA))
                .andExpect(jsonPath("$.productos", hasSize(1)))
                .andExpect(jsonPath("$.productos[0].nombre").value("Alcohol 70° frasco 1 L"))
                .andExpect(jsonPath("$.productos[0].comprar").value(15))
                .andExpect(jsonPath("$.productos[0].aMano").doesNotExist());
    }

    @Test
    void validaLaCantidadYElProducto() throws Exception {
        agregarAMano(guantes, """
                {"cantidad": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cantidad").value("Debe ser mayor que 0"));
        agregarAMano(999_999L, """
                {"cantidad": 1}""").andExpect(status().isNotFound());
    }
}
