package com.evamoreno.inventario.pedido;

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
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

/** Lista de pedidos despachados con búsqueda y rango de fechas. Los días son de Lima (UTC−5). */
class DespachadosTest extends IntegracionTest {

    private static final String URL = "/api/pedidos/despachados";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    private Long producto;
    private String notaSantaRosa;
    private String notaAndes;
    private String notaJunio;

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Despacha un pedido de N unidades (a S/ 11 c/u) y lo deja con esa fecha y hora de despacho. */
    private String despachado(Long cliente, int cantidad, String despachadoEn) throws Exception {
        Long pedido = crear("/api/pedidos", """
                {"clienteId": %d}""".formatted(cliente));
        postJson("/api/pedidos/" + pedido + "/items", """
                {"productoId": %d, "cantidad": %d}""".formatted(producto, cantidad)).andExpect(status().isOk());
        String json = postJson("/api/pedidos/" + pedido + "/despachar", "").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        entityManager.flush();
        jdbc.sql("UPDATE pedido SET despachado_en = ? WHERE id = ?")
                .params(OffsetDateTime.parse(despachadoEn), pedido).update();
        entityManager.clear();
        return JsonPath.read(json, "$.numero");
    }

    @BeforeEach
    void pedidos() throws Exception {
        producto = crear("/api/productos", """
                {"nombre": "Jeringa 5 ml", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "costo": 10}""".formatted(idCategoria("Otros")));
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "L1", "fechaVencimiento": "2028-01-31", "cantidad": 100, "costo": 10}"""
                .formatted(producto)).andExpect(status().isCreated());
        Long santaRosa = crear("/api/clientes", """
                {"nombre": "Botica Santa Rosa", "porcentaje": 10,
                 "locales": [{"nombre": "Sede Norte", "direccion": "Av. Uno 1"}]}""");
        Long andes = crear("/api/clientes", """
                {"nombre": "Clínica Los Andes", "porcentaje": 10}""");

        notaSantaRosa = despachado(santaRosa, 2, "2026-06-15T10:00:00-05:00");
        notaAndes = despachado(andes, 3, "2026-06-14T23:30:00-05:00"); // en UTC ya es 15/06, pero en Lima es 14/06
        notaJunio = despachado(santaRosa, 1, "2026-06-01T09:00:00-05:00");
        crear("/api/pedidos", """
                {"clienteId": %d}""".formatted(andes)); // un borrador no aparece
    }

    @Test
    void listaTodoDelMasRecienteAlMasAntiguoConCadaDia() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos").value(3))
                .andExpect(jsonPath("$.total").value(66.0))
                .andExpect(jsonPath("$.dias[*].fecha", contains("2026-06-15", "2026-06-14", "2026-06-01")))
                .andExpect(jsonPath("$.dias[*].pedidos", contains(1, 1, 1)))
                .andExpect(jsonPath("$.dias[*].total", contains(22.0, 33.0, 11.0)))
                .andExpect(jsonPath("$.lista.contenido[*].numero", contains(notaSantaRosa, notaAndes, notaJunio)))
                .andExpect(jsonPath("$.lista.totalElementos").value(3));
    }

    @Test
    void filtraPorFechasIncluyendoLosDosDias() throws Exception {
        mockMvc.perform(get(URL).param("desde", "2026-06-09").param("hasta", "2026-06-15"))
                .andExpect(jsonPath("$.pedidos").value(2))
                .andExpect(jsonPath("$.total").value(55.0))
                .andExpect(jsonPath("$.lista.contenido[*].numero", contains(notaSantaRosa, notaAndes)));
        // Un solo día: el que se despachó a las 23:30 de Lima es del 14
        mockMvc.perform(get(URL).param("desde", "2026-06-14").param("hasta", "2026-06-14"))
                .andExpect(jsonPath("$.lista.contenido[*].numero", contains(notaAndes)));
        mockMvc.perform(get(URL).param("desde", "2026-06-15").param("hasta", "2026-06-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscaPorClienteLocalONumeroDeNotaEnTodoElHistorial() throws Exception {
        mockMvc.perform(get(URL).param("buscar", "santa ros"))
                .andExpect(jsonPath("$.pedidos").value(2))
                .andExpect(jsonPath("$.lista.contenido[*].numero", contains(notaSantaRosa, notaJunio)));
        mockMvc.perform(get(URL).param("buscar", "norte"))
                .andExpect(jsonPath("$.pedidos").value(2));
        mockMvc.perform(get(URL).param("buscar", "clinica"))
                .andExpect(jsonPath("$.lista.contenido[*].cliente", contains("Clínica Los Andes")));
        mockMvc.perform(get(URL).param("buscar", notaAndes))
                .andExpect(jsonPath("$.lista.contenido[*].numero", contains(notaAndes)));
        // Búsqueda y fechas juntas
        mockMvc.perform(get(URL).param("buscar", "santa").param("desde", "2026-06-01").param("hasta", "2026-06-07"))
                .andExpect(jsonPath("$.lista.contenido[*].numero", contains(notaJunio)));
        mockMvc.perform(get(URL).param("buscar", "nadie"))
                .andExpect(jsonPath("$.pedidos").value(0))
                .andExpect(jsonPath("$.total").value(0.0));
    }

    @Test
    void paginaSinPerderLosTotalesDelResultado() throws Exception {
        mockMvc.perform(get(URL).param("tamano", "2"))
                .andExpect(jsonPath("$.lista.contenido[*].numero", contains(notaSantaRosa, notaAndes)))
                .andExpect(jsonPath("$.lista.totalPaginas").value(2))
                .andExpect(jsonPath("$.dias.length()").value(3));
        mockMvc.perform(get(URL).param("tamano", "2").param("pagina", "1"))
                .andExpect(jsonPath("$.lista.contenido[*].numero", contains(notaJunio)))
                .andExpect(jsonPath("$.pedidos").value(3));
    }
}
