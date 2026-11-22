package com.evamoreno.inventario.stock;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** Entradas registradas con búsqueda por producto o lote y rango de fechas. Los días son de Lima (UTC−5). */
class EntradasRegistradasTest extends IntegracionTest {

    private static final String URL = "/api/movimientos/entradas";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    /** Registra una entrada y la deja con esa fecha y hora. Devuelve el id del movimiento. */
    private long entrada(Long producto, String lote, int cantidad, String costo, String fecha) throws Exception {
        String json = mockMvc.perform(post("/api/movimientos/entrada").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"productoId": %d, "numeroLote": %s, "cantidad": %d, "costo": %s}"""
                        .formatted(producto, lote == null ? "null" : "\"" + lote + "\"", cantidad, costo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.movimientos[0].id")).longValue();
        entityManager.flush();
        jdbc.sql("UPDATE movimiento SET fecha = ? WHERE id = ?").params(OffsetDateTime.parse(fecha), id).update();
        entityManager.clear();
        return id;
    }

    @BeforeEach
    void entradas() throws Exception {
        Long guantes = crear("/api/productos", """
                {"nombre": "Guante de nitrilo talla M", "marca": "Marca G", "categoriaId": %d,
                 "presentacion": "Caja x 100", "unidadMedida": "Caja"}""".formatted(idCategoria("Guantes")));
        Long jeringas = crear("/api/productos", """
                {"nombre": "Jeringa 5 ml", "marca": "Marca A", "categoriaId": %d,
                 "presentacion": "Caja x 100", "unidadMedida": "Caja"}""".formatted(idCategoria("Jeringas")));

        entrada(guantes, "A-12345", 20, "18.50", "2026-06-15T10:32:00-05:00"); //  370.00
        entrada(jeringas, "JX2411", 10, "22", "2026-06-15T10:30:00-05:00"); //     220.00
        // 23:30 de Lima: en UTC ya es 15/06, pero es del 14. Costo de una docena a S/ 25 (25 ÷ 12)
        entrada(jeringas, "JX2411", 3, "2.0833", "2026-06-14T23:30:00-05:00"); //    6.2499
        entrada(guantes, "B777", 5, "10", "2026-06-01T09:00:00-05:00"); //           50.00
        long sinCosto = entrada(jeringas, null, 4, "1", "2026-06-01T08:00:00-05:00");
        jdbc.sql("UPDATE movimiento SET costo_unitario = NULL WHERE id = ?").param(sinCosto).update();
        // La carga inicial (importación desde Excel) no es una compra: no aparece
        long inicial = entrada(guantes, "INICIAL", 50, "5", "2026-06-10T08:00:00-05:00");
        jdbc.sql("UPDATE movimiento SET inicial = true WHERE id = ?").param(inicial).update();
    }

    @Test
    void listaTodoDeLaMasRecienteALaMasAntiguaConCadaDia() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradas").value(5))
                .andExpect(jsonPath("$.total").value(646.25))
                .andExpect(jsonPath("$.sinCosto").value(1))
                .andExpect(jsonPath("$.dias[*].fecha", contains("2026-06-15", "2026-06-14", "2026-06-01")))
                .andExpect(jsonPath("$.dias[*].entradas", contains(2, 1, 2)))
                .andExpect(jsonPath("$.dias[*].total", contains(590.0, 6.25, 50.0)))
                .andExpect(jsonPath("$.dias[*].sinCosto", contains(0, 0, 1)))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(20, 10, 3, 5, 4)))
                .andExpect(jsonPath("$.lista.contenido[0].productoNombre").value("Guante de nitrilo talla M"))
                .andExpect(jsonPath("$.lista.contenido[0].numeroLote").value("A-12345"))
                .andExpect(jsonPath("$.lista.contenido[0].costoUnitario").value(18.5))
                .andExpect(jsonPath("$.lista.totalElementos").value(5));
    }

    @Test
    void filtraPorFechasIncluyendoLosDosDias() throws Exception {
        mockMvc.perform(get(URL).param("desde", "2026-06-09").param("hasta", "2026-06-15"))
                .andExpect(jsonPath("$.entradas").value(3))
                .andExpect(jsonPath("$.total").value(596.25))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(20, 10, 3)));
        // Un solo día: la de las 23:30 de Lima es del 14
        mockMvc.perform(get(URL).param("desde", "2026-06-14").param("hasta", "2026-06-14"))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(3)));
        mockMvc.perform(get(URL).param("desde", "2026-06-15").param("hasta", "2026-06-14"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscaPorProductoOPorLoteEnTodoElHistorial() throws Exception {
        mockMvc.perform(get(URL).param("buscar", "guante"))
                .andExpect(jsonPath("$.entradas").value(2))
                .andExpect(jsonPath("$.total").value(420.0))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(20, 5)));
        mockMvc.perform(get(URL).param("buscar", "jer 5ml"))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(10, 3, 4)));
        mockMvc.perform(get(URL).param("buscar", "marca g"))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(20, 5)));
        // Por lote: una parte del número, con o sin el guion
        mockMvc.perform(get(URL).param("buscar", "12345"))
                .andExpect(jsonPath("$.lista.contenido[*].numeroLote", contains("A-12345")));
        mockMvc.perform(get(URL).param("buscar", "a12345"))
                .andExpect(jsonPath("$.lista.contenido[*].numeroLote", contains("A-12345")));
        mockMvc.perform(get(URL).param("buscar", "jx2411"))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(10, 3)));
        // Búsqueda y fechas juntas
        mockMvc.perform(get(URL).param("buscar", "guante").param("desde", "2026-06-01").param("hasta", "2026-06-07"))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(5)));
        mockMvc.perform(get(URL).param("buscar", "inicial"))
                .andExpect(jsonPath("$.entradas").value(0))
                .andExpect(jsonPath("$.total").value(0.0));
    }

    @Test
    void paginaSinPerderLosTotalesDelResultado() throws Exception {
        mockMvc.perform(get(URL).param("tamano", "2"))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(20, 10)))
                .andExpect(jsonPath("$.lista.totalPaginas").value(3))
                .andExpect(jsonPath("$.dias.length()").value(3));
        mockMvc.perform(get(URL).param("tamano", "2").param("pagina", "1"))
                .andExpect(jsonPath("$.lista.contenido[*].cantidad", contains(3, 5)))
                .andExpect(jsonPath("$.entradas").value(5));
    }
}
