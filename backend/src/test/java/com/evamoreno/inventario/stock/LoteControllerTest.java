package com.evamoreno.inventario.stock;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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

/** Corrección de lotes mal registrados. "Hoy" es el 15/06/2026 (reloj fijo de los tests). */
class LoteControllerTest extends IntegracionTest {

    private static final LocalDate MARZO_2028 = LocalDate.of(2028, 3, 31);
    private static final LocalDate MAYO_2029 = LocalDate.of(2029, 5, 31);

    private Long productoId;

    @BeforeEach
    void crearProducto() throws Exception {
        productoId = crear("/api/productos", """
                {"nombre": "Guantes de nitrilo talla M", "marca": "Marca A", "categoriaId": %d,
                 "presentacion": "Caja x 100", "unidadMedida": "Caja", "costo": 5}"""
                .formatted(idCategoria("Guantes")));
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions corregir(Long loteId, String json) throws Exception {
        return mockMvc.perform(put("/api/lotes/{id}", loteId).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long entrada(String lote, LocalDate vence, int cantidad, String costo) throws Exception {
        String json = postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "%s", "fechaVencimiento": "%s", "cantidad": %d, "costo": %s}"""
                .formatted(productoId, lote, vence, cantidad, costo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.movimientos[0].loteId")).longValue();
    }

    private ResultActions historial() throws Exception {
        return mockMvc.perform(get("/api/movimientos").param("productoId", productoId.toString()));
    }

    // ------------------------------------------------------------------ corregir

    @Test
    void corrigeNumeroVencimientoYCantidadYElCambioDeCantidadQuedaComoAjuste() throws Exception {
        Long lote = entrada("L-1", MARZO_2028, 10, "5");

        corregir(lote, """
                {"numeroLote": " l-2 ", "fechaVencimiento": "2029-05-01", "cantidad": 7, "nota": "Error en el Excel"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lote))
                .andExpect(jsonPath("$.numeroLote").value("L-2"))
                .andExpect(jsonPath("$.fechaVencimiento").value("2029-05-31")) // solo cuentan mes y año
                .andExpect(jsonPath("$.cantidad").value(7))
                .andExpect(jsonPath("$.costoPromedio").value(5.0)); // el costo no cambia

        mockMvc.perform(get("/api/productos/{id}", productoId))
                .andExpect(jsonPath("$.stockDisponible").value(7))
                .andExpect(jsonPath("$.lotes[*].numeroLote", contains("L-2")));
        historial()
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.contenido[0].cantidad").value(-3))
                .andExpect(jsonPath("$.contenido[0].nota").value("Error en el Excel"))
                .andExpect(jsonPath("$.contenido[*].numeroLote", everyItem(is("L-2"))));
    }

    @Test
    void siSoloCambiaElNumeroNoHayAjusteYSinMotivoSeAnotaComoCorreccion() throws Exception {
        Long lote = entrada("L-1", MARZO_2028, 10, "5");

        corregir(lote, """
                {"numeroLote": "L-1A", "fechaVencimiento": "2028-03-31", "cantidad": 10}""")
                .andExpect(status().isOk());
        historial().andExpect(jsonPath("$.totalElementos").value(1));

        corregir(lote, """
                {"numeroLote": "L-1A", "fechaVencimiento": "2028-03-31", "cantidad": 12, "nota": "   "}""")
                .andExpect(status().isOk());
        historial()
                .andExpect(jsonPath("$.contenido[0].cantidad").value(2))
                .andExpect(jsonPath("$.contenido[0].nota").value("Corrección del lote"));
    }

    @Test
    void conCantidadCeroElLoteDejaDeListarseYSePuedeQuitarElVencimientoOElNumero() throws Exception {
        Long lote = entrada("L-1", MARZO_2028, 10, "5");
        Long otro = entrada("L-2", MAYO_2029, 4, "5");

        corregir(lote, """
                {"numeroLote": "L-1", "fechaVencimiento": "2028-03-31", "cantidad": 0}""")
                .andExpect(status().isOk());
        corregir(otro, """
                {"numeroLote": "", "fechaVencimiento": null, "cantidad": 4}""")
                .andExpect(jsonPath("$.numeroLote").doesNotExist())
                .andExpect(jsonPath("$.fechaVencimiento").doesNotExist());

        mockMvc.perform(get("/api/productos/{id}", productoId))
                .andExpect(jsonPath("$.stockDisponible").value(4))
                .andExpect(jsonPath("$.lotes.length()").value(1));
    }

    @Test
    void validaLosDatos() throws Exception {
        Long lote = entrada("L-1", MARZO_2028, 10, "5");

        corregir(lote, """
                {"numeroLote": "L-1", "cantidad": -1}""")
                .andExpect(status().isBadRequest());
        corregir(lote, """
                {"numeroLote": "L#1", "cantidad": 1}""")
                .andExpect(status().isBadRequest());
        corregir(999_999L, """
                {"numeroLote": "L-1", "cantidad": 1}""")
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ unir

    @Test
    void siElNumeroYaExistePideConfirmarYLuegoUneLosDosLotes() throws Exception {
        Long bueno = entrada("L-1", MARZO_2028, 10, "5");
        Long repetido = entrada("L-11", MAYO_2029, 5, "8"); // el mismo lote, mal escrito

        String json = """
                {"numeroLote": "L-1", "fechaVencimiento": "2028-03-31", "cantidad": 5%s}""";
        corregir(repetido, json.formatted(""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe el lote L-1 en este producto, con 10 unidades "
                        + "(vence 03/2028). Confirma para unir los dos en uno solo."));

        corregir(repetido, json.formatted(", \"unir\": true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bueno))
                .andExpect(jsonPath("$.numeroLote").value("L-1"))
                .andExpect(jsonPath("$.cantidad").value(15))
                .andExpect(jsonPath("$.costoPromedio").value(6.0)); // (10 × 5 + 5 × 8) / 15

        mockMvc.perform(get("/api/productos/{id}", productoId))
                .andExpect(jsonPath("$.stockDisponible").value(15))
                .andExpect(jsonPath("$.lotes[*].numeroLote", contains("L-1")));
        // Las dos entradas quedan en el historial del lote que quedó
        historial()
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].loteId", everyItem(is(bueno.intValue()))));
    }

    @Test
    void alUnirMandaElVencimientoYLaCantidadQueSeEscribieron() throws Exception {
        entrada("L-1", MARZO_2028, 10, "5");
        Long repetido = entrada("L-11", MARZO_2028, 5, "5");

        corregir(repetido, """
                {"numeroLote": "L-1", "fechaVencimiento": "2029-05-31", "cantidad": 3, "unir": true}""")
                .andExpect(jsonPath("$.cantidad").value(13))
                .andExpect(jsonPath("$.fechaVencimiento").value("2029-05-31"));
        historial()
                .andExpect(jsonPath("$.contenido[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.contenido[0].cantidad").value(-2));
    }

    @Test
    void conUnLoteQueYaNoTieneUnidadesSeUnenSinPreguntar() throws Exception {
        Long agotado = entrada("L-1", MARZO_2028, 4, "5");
        postJson("/api/movimientos/salida", """
                {"productoId": %d, "cantidad": 4, "loteId": %d}""".formatted(productoId, agotado))
                .andExpect(status().isCreated());
        Long lote = entrada("L-X", MARZO_2028, 6, "5");

        corregir(lote, """
                {"numeroLote": "L-1", "fechaVencimiento": "2028-03-31", "cantidad": 6}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agotado))
                .andExpect(jsonPath("$.cantidad").value(6));
    }

    // ------------------------------------------------------------------ notas ya despachadas

    @Test
    void lasNotasYaDespachadasMuestranElLoteCorregido() throws Exception {
        Long viejo = entrada("J-VIEJO", MARZO_2028, 3, "5");
        Long nuevo = entrada("J-NUEVO", MAYO_2029, 10, "5");
        Long cliente = crear("/api/clientes", """
                {"nombre": "Botica Demo", "porcentaje": 15, "locales": [{"direccion": "Av. Demo 123"}]}""");
        Long pedido = crear("/api/pedidos", """
                {"clienteId": %d}""".formatted(cliente));
        postJson("/api/pedidos/" + pedido + "/items", """
                {"productoId": %d, "cantidad": 5}""".formatted(productoId)).andExpect(status().isOk());
        postJson("/api/pedidos/" + pedido + "/despachar", "")
                .andExpect(jsonPath("$.items[0].lotes[*].numeroLote", contains("J-VIEJO", "J-NUEVO")));

        corregir(nuevo, """
                {"numeroLote": "J-2029", "fechaVencimiento": "2029-05-31", "cantidad": 8}""")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos/{id}", pedido))
                .andExpect(jsonPath("$.items[0].lotes[*].numeroLote", contains("J-VIEJO", "J-2029")));

        // J-VIEJO era el mismo lote que J-2029: al unirlos, la nota queda con un solo lote
        corregir(viejo, """
                {"numeroLote": "J-2029", "fechaVencimiento": "2029-05-31", "cantidad": 0, "unir": true}""")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pedidos/{id}", pedido))
                .andExpect(jsonPath("$.items[0].lotes[*].numeroLote", contains("J-2029")))
                .andExpect(jsonPath("$.items[0].lotes[0].cantidad").value(5));
        mockMvc.perform(get("/api/pedidos/{id}/nota.pdf", pedido)).andExpect(status().isOk());
    }

    @Test
    void sinSesionNoSePuedeCorregir() throws Exception {
        Long lote = entrada("L-1", MARZO_2028, 10, "5");

        mockMvcSinSesion.perform(put("/api/lotes/{id}", lote).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numeroLote\": \"X\", \"cantidad\": 1}"))
                .andExpect(status().isForbidden()); // sin token CSRF ni sesión
        mockMvc.perform(get("/api/productos/{id}", productoId))
                .andExpect(jsonPath("$.lotes[*].numeroLote", contains("L-1")));
    }

    @Test
    void elMensajeDeUnirNombraElLoteSinNumeroYSinVencimiento() throws Exception {
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "cantidad": 1, "costo": 5}""".formatted(productoId))
                .andExpect(status().isCreated());
        Long lote = entrada("L-1", MARZO_2028, 10, "5");

        corregir(lote, """
                {"numeroLote": "", "cantidad": 10}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe el lote (sin número) en este producto, con 1 unidad "
                        + "(sin vencimiento). Confirma para unir los dos en uno solo."));
    }

    // ------------------------------------------------------------------ dar de baja un vencido

    @Test
    void darDeBajaUnVencidoLoDejaEnCeroConUnAjusteBajaPorVencido() throws Exception {
        Long vencido = entrada("V-1", LocalDate.of(2026, 3, 31), 5, "4"); // venció en marzo; hoy es 15/06/2026
        postJson("/api/lotes/" + vencido + "/baja", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientos[0].tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.movimientos[0].cantidad").value(-5))
                .andExpect(jsonPath("$.movimientos[0].nota").value("Baja por vencido"))
                .andExpect(jsonPath("$.stockDisponible").value(0));
        historial()
                .andExpect(jsonPath("$.contenido[0].nota").value("Baja por vencido"))
                .andExpect(jsonPath("$.contenido[0].stockResultanteLote").value(0))
                .andExpect(jsonPath("$.contenido[0].costoUnitario").value(4.0));

        // Ya está en 0
        postJson("/api/lotes/" + vencido + "/baja", "").andExpect(status().isConflict());
    }

    @Test
    void unLoteQueNoVencioNoSeDaDeBaja() throws Exception {
        Long vigente = entrada("L-9", MARZO_2028, 5, "4");
        postJson("/api/lotes/" + vigente + "/baja", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("todavía no vence")));
        mockMvc.perform(get("/api/productos/{id}", productoId)).andExpect(jsonPath("$.stockDisponible").value(5));
    }
}
