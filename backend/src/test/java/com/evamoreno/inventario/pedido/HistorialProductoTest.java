package com.evamoreno.inventario.pedido;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/**
 * Historial de un producto con el pedido y el cliente de cada salida o devolución, y lo dado de baja por vencido
 * en el Resumen. "Hoy" es el 15/06/2026 (reloj fijo de los tests).
 */
class HistorialProductoTest extends IntegracionTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long loteDe(ResultActions entrada) throws Exception {
        return ((Number) JsonPath.read(entrada.andReturn().getResponse().getContentAsString(),
                "$.movimientos[0].loteId")).longValue();
    }

    @Test
    void elHistorialMuestraElPedidoYElClienteYLaBajaPorVencidoCuentaEnElResumen() throws Exception {
        Long jeringa = crear("/api/productos", """
                {"nombre": "Jeringa 5 ml", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "costo": 10}""".formatted(idCategoria("Jeringas")));
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "L1", "fechaVencimiento": "2028-01-31", "cantidad": 20, "costo": 10}"""
                .formatted(jeringa)).andExpect(status().isCreated());
        Long vencido = loteDe(postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "V1", "fechaVencimiento": "2026-03-31", "cantidad": 3, "costo": 8}"""
                .formatted(jeringa)).andExpect(status().isCreated()));

        Long botica = crear("/api/clientes", """
                {"nombre": "Botica Santa Rosa", "porcentaje": 10}""");
        Long pedido = crear("/api/pedidos", """
                {"clienteId": %d}""".formatted(botica));
        postJson("/api/pedidos/" + pedido + "/items", """
                {"productoId": %d, "cantidad": 5}""".formatted(jeringa)).andExpect(status().isOk());
        String despacho = postJson("/api/pedidos/" + pedido + "/despachar", "").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String numero = JsonPath.read(despacho, "$.numero");
        Number item = JsonPath.read(despacho, "$.items[0].id");
        postJson("/api/pedidos/" + pedido + "/correcciones", """
                {"cambios": [{"itemId": %d, "cantidad": 4}]}""".formatted(item.longValue()))
                .andExpect(status().isOk());
        postJson("/api/lotes/" + vencido + "/baja", "").andExpect(status().isOk());

        mockMvc.perform(get("/api/movimientos").param("productoId", jeringa.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].tipo", contains("AJUSTE", "DEVOLUCION", "SALIDA", "ENTRADA",
                        "ENTRADA")))
                .andExpect(jsonPath("$.contenido[0].nota").value("Baja por vencido"))
                .andExpect(jsonPath("$.contenido[0].pedidoId").doesNotExist())
                .andExpect(jsonPath("$.contenido[1].pedidoId").value(pedido))
                .andExpect(jsonPath("$.contenido[1].pedidoNumero").value(numero))
                .andExpect(jsonPath("$.contenido[1].cliente").value("Botica Santa Rosa"))
                .andExpect(jsonPath("$.contenido[1].cantidad").value(1))
                .andExpect(jsonPath("$.contenido[2].cliente").value("Botica Santa Rosa"))
                .andExpect(jsonPath("$.contenido[2].cantidad").value(5))
                .andExpect(jsonPath("$.contenido[3].inicial").value(false))
                .andExpect(jsonPath("$.contenido[3].cliente").doesNotExist());

        // El Resumen suma lo dado de baja en el periodo (3 × S/ 8); ya no queda nada vencido sin vender
        entityManager.flush();
        jdbc.sql("UPDATE movimiento SET fecha = ? WHERE tipo = 'AJUSTE' AND lote_id = ?")
                .params(OffsetDateTime.parse("2026-06-10T10:00:00-05:00"), vencido).update();
        entityManager.clear();
        mockMvc.perform(get("/api/resumen/detalles").param("periodo", "MES"))
                .andExpect(jsonPath("$.inventario.valorDadoDeBaja").value(24.0))
                .andExpect(jsonPath("$.inventario.valorVencido").value(0.0))
                .andExpect(jsonPath("$.inventario.lotesVencidos").value(0));
    }
}
