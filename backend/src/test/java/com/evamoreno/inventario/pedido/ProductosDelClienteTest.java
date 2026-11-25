package com.evamoreno.inventario.pedido;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** Los productos que compra un cliente y todas sus ventas de un producto. Los días son de Lima. */
class ProductosDelClienteTest extends IntegracionTest {

    private static final String PEDIDOS = "/api/pedidos";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    private Long botica;
    private Long guantes;
    private Long jeringas;

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long producto(String nombre, String costo) throws Exception {
        Long id = crear("/api/productos", """
                {"nombre": "%s", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "costo": %s}""".formatted(nombre, idCategoria("Otros"), costo));
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "L%d", "cantidad": 100, "costo": %s}""".formatted(id, id, costo))
                .andExpect(status().isCreated());
        return id;
    }

    /** Un pedido de un solo producto, despachado ese día (a las 10:00 de Lima). Devuelve el JSON del despacho. */
    private String despachado(Long cliente, Long producto, int cantidad, String precio, String dia) throws Exception {
        Long pedido = crear(PEDIDOS, """
                {"clienteId": %d}""".formatted(cliente));
        String conItem = postJson(PEDIDOS + "/" + pedido + "/items", """
                {"productoId": %d, "cantidad": %d}""".formatted(producto, cantidad))
                .andReturn().getResponse().getContentAsString();
        if (precio != null) {
            Number item = JsonPath.read(conItem, "$.items[0].id");
            mockMvc.perform(put(PEDIDOS + "/" + pedido + "/items/" + item).contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"cantidad": %d, "precio": %s}""".formatted(cantidad, precio)))
                    .andExpect(status().isOk());
        }
        String json = postJson(PEDIDOS + "/" + pedido + "/despachar", "").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        entityManager.flush();
        jdbc.sql("UPDATE pedido SET despachado_en = ? WHERE id = ?")
                .params(OffsetDateTime.parse(dia + "T10:00:00-05:00"), pedido).update();
        entityManager.clear();
        return json;
    }

    @BeforeEach
    void ventas() throws Exception {
        guantes = producto("Guante de nitrilo talla M", "10");
        jeringas = producto("Jeringa 5 ml", "20");
        botica = crear("/api/clientes", """
                {"nombre": "Botica Santa Rosa", "porcentaje": 10}""");

        despachado(botica, guantes, 5, null, "2026-06-01"); //   5 × 11.00 (calculado)
        despachado(botica, jeringas, 2, null, "2026-06-01"); //  2 × 22.00
        despachado(botica, guantes, 3, "10.50", "2026-06-10"); // 3 × 10.50 (escrito a mano)

        // Un ítem quitado en una corrección no cuenta, aunque sea el más reciente
        String quitado = despachado(botica, jeringas, 1, null, "2026-06-12");
        Number pedido = JsonPath.read(quitado, "$.id");
        Number item = JsonPath.read(quitado, "$.items[0].id");
        postJson(PEDIDOS + "/" + pedido + "/correcciones", """
                {"cambios": [{"itemId": %d, "cantidad": 0}]}""".formatted(item.longValue()))
                .andExpect(status().isOk());

        // Ni los borradores ni lo que compró otro cliente
        Long borrador = crear(PEDIDOS, """
                {"clienteId": %d}""".formatted(botica));
        postJson(PEDIDOS + "/" + borrador + "/items", """
                {"productoId": %d, "cantidad": 9}""".formatted(guantes)).andExpect(status().isOk());
        Long otro = crear("/api/clientes", """
                {"nombre": "Clínica Los Andes", "porcentaje": 10}""");
        despachado(otro, guantes, 7, null, "2026-06-14");
    }

    @Test
    void listaCadaProductoConSuUltimoPrecioFechaYCantidad() throws Exception {
        mockMvc.perform(get("/api/clientes/{id}/productos", botica))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Guante de nitrilo talla M", "Jeringa 5 ml")))
                .andExpect(jsonPath("$.contenido[0].ultimoPrecio").value(10.5))
                .andExpect(jsonPath("$.contenido[0].ultimaVenta").value("2026-06-10"))
                .andExpect(jsonPath("$.contenido[0].ultimaCantidad").value(3))
                .andExpect(jsonPath("$.contenido[0].pedidos").value(2))
                .andExpect(jsonPath("$.contenido[0].unidadMedida").value("Caja"))
                .andExpect(jsonPath("$.contenido[1].ultimoPrecio").value(22.0))
                .andExpect(jsonPath("$.contenido[1].ultimaVenta").value("2026-06-01"))
                .andExpect(jsonPath("$.contenido[1].pedidos").value(1));

        mockMvc.perform(get("/api/clientes/{id}/productos", botica).param("buscar", "jer 5ml"))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Jeringa 5 ml")));
        mockMvc.perform(get("/api/clientes/{id}/productos", botica).param("tamano", "1").param("pagina", "1"))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Jeringa 5 ml")))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void lasVentasDeUnProductoVanDeLaMasRecienteALaMasAntigua() throws Exception {
        mockMvc.perform(get("/api/clientes/{id}/productos/{producto}/ventas", botica, guantes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].fecha", contains("2026-06-10", "2026-06-01")))
                .andExpect(jsonPath("$[*].cantidad", contains(3, 5)))
                .andExpect(jsonPath("$[*].precio", contains(10.5, 11.0)))
                .andExpect(jsonPath("$[*].total", contains(31.5, 55.0)))
                .andExpect(jsonPath("$[0].numero").isNotEmpty());
        mockMvc.perform(get("/api/clientes/{id}/productos/{producto}/ventas", botica, jeringas))
                .andExpect(jsonPath("$[*].cantidad", contains(2)));
    }

    @Test
    void unClienteQueNoExisteDa404() throws Exception {
        mockMvc.perform(get("/api/clientes/{id}/productos", 999_999)).andExpect(status().isNotFound());
    }
}
