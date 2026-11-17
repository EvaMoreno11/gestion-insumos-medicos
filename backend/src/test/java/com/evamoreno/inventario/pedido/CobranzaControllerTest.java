package com.evamoreno.inventario.pedido;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** Pagos, plazos y la lista de cobranzas. "Hoy" es el 15/06/2026 (reloj fijo de los tests). */
class CobranzaControllerTest extends IntegracionTest {

    private static final String PEDIDOS = "/api/pedidos";
    private static final String COBRANZAS = "/api/cobranzas";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    // ------------------------------------------------------------------ ayudas

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions plazo(Long pedido, String dias) throws Exception {
        return putJson(PEDIDOS + "/" + pedido + "/plazo", """
                {"plazoDias": %s}""".formatted(dias));
    }

    private ResultActions pago(Long pedido, String monto, String medio) throws Exception {
        return postJson(PEDIDOS + "/" + pedido + "/pagos", """
                {"monto": %s, "medio": "%s"}""".formatted(monto, medio));
    }

    private Long jeringas() throws Exception {
        Long producto = crear("/api/productos", """
                {"nombre": "Jeringa 5 ml", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "costo": 10}""".formatted(idCategoria("Otros")));
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "L1", "cantidad": 500, "costo": 10}""".formatted(producto))
                .andExpect(status().isCreated());
        return producto;
    }

    private Long cliente(String nombre) throws Exception {
        return crear("/api/clientes", """
                {"nombre": "%s", "porcentaje": 10}""".formatted(nombre));
    }

    /** Pedido de jeringas a S/ 11 cada una (costo 10 + 10 %). */
    private Long pedido(Long clienteId, Long productoId, int cantidad) throws Exception {
        Long pedido = crear(PEDIDOS, """
                {"clienteId": %d}""".formatted(clienteId));
        postJson(PEDIDOS + "/" + pedido + "/items", """
                {"productoId": %d, "cantidad": %d}""".formatted(productoId, cantidad)).andExpect(status().isOk());
        return pedido;
    }

    private ResultActions despachar(Long pedido) throws Exception {
        return postJson(PEDIDOS + "/" + pedido + "/despachar", "").andExpect(jsonPath("$.estado").value("DESPACHADO"));
    }

    /** Mueve la fecha de despacho (12:00 de Lima de ese día). */
    private void despachadoEl(Long pedido, String dia) {
        entityManager.flush();
        jdbc.update("UPDATE pedido SET despachado_en = ? WHERE id = ?",
                Timestamp.from(Instant.parse(dia + "T17:00:00Z")), pedido);
        entityManager.clear(); // que el pedido se vuelva a leer con la fecha nueva
    }

    // ------------------------------------------------------------------ tests

    @Test
    void unaNotaPasaDeSinPlazoACreditoVencidoYPagado() throws Exception {
        Long jeringas = jeringas();
        Long botica = cliente("Botica Santa Rosa");
        Long nota = pedido(botica, jeringas, 10);

        // Un borrador no tiene cobro ni admite pagos
        mockMvc.perform(get(PEDIDOS + "/{id}", nota)).andExpect(jsonPath("$.cobro").value(nullValue()));
        pago(nota, "10", "EFECTIVO").andExpect(status().isConflict());

        // Se despacha sin preguntar nada: queda por cobrar, sin plazo
        despachar(nota)
                .andExpect(jsonPath("$.cobro.total").value(110.0))
                .andExpect(jsonPath("$.cobro.saldo").value(110.0))
                .andExpect(jsonPath("$.cobro.estado").value("SIN_PLAZO"))
                .andExpect(jsonPath("$.cobro.vence").value(nullValue()))
                .andExpect(jsonPath("$.cobro.pagos", empty()));
        mockMvc.perform(get(COBRANZAS))
                .andExpect(jsonPath("$.totalPorCobrar").value(110.0))
                .andExpect(jsonPath("$.totalVencido").value(0.0))
                .andExpect(jsonPath("$.clientesVencidos").value(0))
                .andExpect(jsonPath("$.sinPlazo", hasSize(1)))
                .andExpect(jsonPath("$.sinPlazo[0].cliente").value("Botica Santa Rosa"))
                .andExpect(jsonPath("$.sinPlazo[0].saldo").value(110.0))
                .andExpect(jsonPath("$.vencidos", empty()))
                .andExpect(jsonPath("$.porCobrar", empty()));

        // Crédito a 15 días: vence el 30/06 y queda como el plazo habitual del cliente
        plazo(nota, "15")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobro.estado").value("POR_COBRAR"))
                .andExpect(jsonPath("$.cobro.vence").value("2026-06-30"))
                .andExpect(jsonPath("$.cobro.dias").value(15));
        mockMvc.perform(get("/api/clientes/{id}", botica)).andExpect(jsonPath("$.plazoDias").value(15));

        // Pago parcial por Yape
        pago(nota, "50", "YAPE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobro.pagado").value(50.0))
                .andExpect(jsonPath("$.cobro.saldo").value(60.0))
                .andExpect(jsonPath("$.cobro.pagos[0].medio").value("YAPE"))
                .andExpect(jsonPath("$.cobro.pagos[0].fecha").value("2026-06-15"));
        pago(nota, "100", "EFECTIVO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("es mayor que lo que falta cobrar (S/ 60.00)")));
        postJson(PEDIDOS + "/" + nota + "/pagos", """
                {"monto": 10, "medio": "PLIN", "fecha": "2026-06-16"}""").andExpect(status().isBadRequest());
        postJson(PEDIDOS + "/" + nota + "/pagos", """
                {"monto": 0, "medio": "PLIN"}""").andExpect(status().isBadRequest());

        // Pasan los días: se despachó el 20/05, venció el 04/06 (hace 11 días)
        despachadoEl(nota, "2026-05-20");
        mockMvc.perform(get(COBRANZAS))
                .andExpect(jsonPath("$.totalPorCobrar").value(60.0))
                .andExpect(jsonPath("$.totalVencido").value(60.0))
                .andExpect(jsonPath("$.clientesVencidos").value(1))
                .andExpect(jsonPath("$.vencidos[0].pedidoId").value(nota.intValue()))
                .andExpect(jsonPath("$.vencidos[0].vence").value("2026-06-04"))
                .andExpect(jsonPath("$.vencidos[0].dias").value(-11))
                .andExpect(jsonPath("$.vencidos[0].pagado").value(50.0))
                .andExpect(jsonPath("$.sinPlazo", empty()));
        mockMvc.perform(get(PEDIDOS + "/{id}", nota)).andExpect(jsonPath("$.cobro.estado").value("VENCIDO"));

        // Paga el resto en efectivo: sale de cobranzas
        Long ultimoPago = ((Number) JsonPath.read(pago(nota, "60", "EFECTIVO")
                .andExpect(jsonPath("$.cobro.estado").value("PAGADO"))
                .andExpect(jsonPath("$.cobro.saldo").value(0.0))
                .andReturn().getResponse().getContentAsString(), "$.cobro.pagos[1].id")).longValue();
        mockMvc.perform(get(COBRANZAS))
                .andExpect(jsonPath("$.totalPorCobrar").value(0.0))
                .andExpect(jsonPath("$.clientesVencidos").value(0))
                .andExpect(jsonPath("$.vencidos", empty()));
        pago(nota, "1", "EFECTIVO").andExpect(status().isConflict());

        // Un pago mal registrado se puede borrar
        mockMvc.perform(delete(PEDIDOS + "/{id}/pagos/{pago}", nota, ultimoPago))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cobro.saldo").value(60.0))
                .andExpect(jsonPath("$.cobro.estado").value("VENCIDO"));
        mockMvc.perform(delete(PEDIDOS + "/{id}/pagos/{pago}", nota, 999999)).andExpect(status().isNotFound());
    }

    @Test
    void elSiguientePedidoTomaElPlazoHabitualYSePuedeCambiar() throws Exception {
        Long jeringas = jeringas();
        Long botica = cliente("Botica Santa Rosa");
        Long primera = pedido(botica, jeringas, 1);
        despachar(primera);
        plazo(primera, "7").andExpect(jsonPath("$.cobro.vence").value("2026-06-22"));

        // El siguiente pedido toma los 7 días al despacharse, sin que nadie lo anote
        Long segunda = pedido(botica, jeringas, 2);
        despachar(segunda)
                .andExpect(jsonPath("$.cobro.plazoDias").value(7))
                .andExpect(jsonPath("$.cobro.estado").value("POR_COBRAR"))
                .andExpect(jsonPath("$.cobro.vence").value("2026-06-22"));
        mockMvc.perform(get(COBRANZAS))
                .andExpect(jsonPath("$.porCobrar", hasSize(2)))
                .andExpect(jsonPath("$.totalPorCobrar").value(33.0));

        // Se cambia a contado: vence hoy (aún no está vencido) y pasa a ser el habitual
        plazo(segunda, "0")
                .andExpect(jsonPath("$.cobro.vence").value("2026-06-15"))
                .andExpect(jsonPath("$.cobro.dias").value(0))
                .andExpect(jsonPath("$.cobro.estado").value("POR_COBRAR"));
        mockMvc.perform(get("/api/clientes/{id}", botica)).andExpect(jsonPath("$.plazoDias").value(0));

        // Quitar el plazo de una nota no borra el habitual del cliente
        plazo(segunda, "null").andExpect(jsonPath("$.cobro.estado").value("SIN_PLAZO"));
        mockMvc.perform(get("/api/clientes/{id}", botica)).andExpect(jsonPath("$.plazoDias").value(0));
        plazo(segunda, "400").andExpect(status().isBadRequest());

        // También se edita en la ficha del cliente
        putJson("/api/clientes/" + botica, """
                {"nombre": "Botica Santa Rosa", "porcentaje": 10, "plazoDias": 30}""")
                .andExpect(jsonPath("$.plazoDias").value(30));
    }
}
