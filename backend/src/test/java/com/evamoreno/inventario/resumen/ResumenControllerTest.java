package com.evamoreno.inventario.resumen;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.evamoreno.inventario.stock.EntradaRequest;
import com.evamoreno.inventario.stock.MovimientoService;
import com.jayway.jsonpath.JsonPath;

/** "Hoy" es el lunes 15/06/2026 (reloj fijo de los tests, zona America/Lima). */
class ResumenControllerTest extends IntegracionTest {

    private static final String URL = "/api/resumen";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MovimientoService movimientos;

    // ------------------------------------------------------------------ ayudas

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static Long id(ResultActions resultado, String ruta) throws Exception {
        return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta)).longValue();
    }

    private Long producto(String nombre, String costo) throws Exception {
        return crear("/api/productos", """
                {"nombre": "%s", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "costo": %s}""".formatted(nombre, idCategoria("Otros"), costo));
    }

    /** Compra registrada en la app. Devuelve el id del movimiento. */
    private Long compra(Long productoId, String lote, String vence, int cantidad, String costo) throws Exception {
        return id(postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "%s", "fechaVencimiento": %s, "cantidad": %d, "costo": %s}"""
                .formatted(productoId, lote, vence == null ? "null" : "\"" + vence + "\"", cantidad, costo))
                .andExpect(status().isCreated()), "$.movimientos[0].id");
    }

    private Long cliente(String nombre) throws Exception {
        return crear("/api/clientes", """
                {"nombre": "%s", "porcentaje": 10}""".formatted(nombre));
    }

    private Long pedido(Long clienteId, Long productoId, int cantidad) throws Exception {
        Long pedido = crear("/api/pedidos", """
                {"clienteId": %d}""".formatted(clienteId));
        postJson("/api/pedidos/" + pedido + "/items", """
                {"productoId": %d, "cantidad": %d}""".formatted(productoId, cantidad)).andExpect(status().isOk());
        return pedido;
    }

    private void despachar(Long pedido) throws Exception {
        postJson("/api/pedidos/" + pedido + "/despachar", "").andExpect(jsonPath("$.estado").value("DESPACHADO"));
    }

    /** Fija la fecha de un movimiento (12:00 de Lima de ese día). */
    private void fechadoEl(Long movimiento, String dia) {
        jdbc.update("UPDATE movimiento SET fecha = ? WHERE id = ?",
                Timestamp.from(Instant.parse(dia + "T17:00:00Z")), movimiento);
    }

    /** Mueve la fecha de despacho (12:00 de Lima de ese día). */
    private void despachadoEl(Long pedido, String dia) {
        jdbc.update("UPDATE pedido SET despachado_en = ? WHERE id = ?",
                Timestamp.from(Instant.parse(dia + "T17:00:00Z")), pedido);
    }

    // ------------------------------------------------------------------ pantalla principal

    @Test
    void resumenDelMesConGananciaComprasYComparacionConElMesPasado() throws Exception {
        Long jeringa = producto("Jeringa 5 ml", "10");
        Long gasa = producto("Gasa estéril", "4");
        // La fecha de los movimientos la pone la base de datos (hora real): se fija para el reloj de los tests
        fechadoEl(compra(jeringa, "L1", null, 20, "10"), "2026-06-10"); // compra de este mes: S/ 200
        movimientos.registrarEntradaInicial(new EntradaRequest(jeringa, "INI", null, 100, new BigDecimal("10"),
                "Importación de inventario (fila 2)"));                 // stock inicial: no es una compra
        jdbc.update("UPDATE movimiento SET fecha = ? WHERE inicial",
                Timestamp.from(Instant.parse("2026-06-02T17:00:00Z")));
        fechadoEl(compra(gasa, "G1", null, 5, "4"), "2026-05-05");     // S/ 20 en mayo

        Long botica = cliente("Botica Demo");
        // Este mes: 5 jeringas a 11 (costaron 10) + un ítem escrito a mano de S/ 30 (sin costo)
        Long deJunio = pedido(botica, jeringa, 5);
        postJson("/api/pedidos/" + deJunio + "/items", """
                {"descripcion": "Termómetro digital", "cantidad": 1, "precio": 30}""");
        despachar(deJunio);
        // Mes pasado: el 10/05 (dentro del 1 al 15, se compara) y el 20/05 (solo cuenta en el gráfico)
        Long del10DeMayo = pedido(botica, jeringa, 2);
        despachar(del10DeMayo);
        despachadoEl(del10DeMayo, "2026-05-10");
        Long del20DeMayo = pedido(botica, jeringa, 1);
        despachar(del20DeMayo);
        despachadoEl(del20DeMayo, "2026-05-20");
        pedido(botica, jeringa, 3); // un borrador no es una venta

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.desde").value("2026-06-01"))
                .andExpect(jsonPath("$.periodo.hasta").value("2026-06-15"))
                .andExpect(jsonPath("$.periodo.anteriorDesde").value("2026-05-01"))
                .andExpect(jsonPath("$.periodo.anteriorHasta").value("2026-05-15"))
                .andExpect(jsonPath("$.periodo.nombreAnterior").value("el mes pasado"))
                .andExpect(jsonPath("$.ventas.total").value(85.0))
                .andExpect(jsonPath("$.ventas.pedidos").value(1))
                .andExpect(jsonPath("$.ventas.totalAnterior").value(22.0))
                .andExpect(jsonPath("$.ventas.pedidosAnterior").value(1))
                .andExpect(jsonPath("$.ganancia.ventasConCosto").value(55.0))
                .andExpect(jsonPath("$.ganancia.costoVendido").value(50.0))
                .andExpect(jsonPath("$.ganancia.ganancia").value(5.0))
                .andExpect(jsonPath("$.ganancia.margen").value(9.1))
                .andExpect(jsonPath("$.ganancia.ventasSinCosto").value(30.0))
                .andExpect(jsonPath("$.ganancia.gananciaAnterior").value(2.0))
                .andExpect(jsonPath("$.compras.total").value(200.0))
                .andExpect(jsonPath("$.compras.entradas").value(1))
                .andExpect(jsonPath("$.compras.totalAnterior").value(20.0))
                .andExpect(jsonPath("$.ventasPorMes", hasSize(12)))
                .andExpect(jsonPath("$.ventasPorMes[0].mes").value("2025-07"))
                .andExpect(jsonPath("$.ventasPorMes[10].mes").value("2026-05"))
                .andExpect(jsonPath("$.ventasPorMes[10].ventas").value(33.0))   // 22 + 11
                .andExpect(jsonPath("$.ventasPorMes[10].ganancia").value(3.0))
                .andExpect(jsonPath("$.ventasPorMes[11].mes").value("2026-06"))
                .andExpect(jsonPath("$.ventasPorMes[11].ventas").value(85.0))
                .andExpect(jsonPath("$.ventasPorMes[11].ganancia").value(5.0))
                .andExpect(jsonPath("$.ventasPorMes[9].ventas").value(0.0));

        // El mes pasado completo
        mockMvc.perform(get(URL).param("periodo", "MES_ANTERIOR"))
                .andExpect(jsonPath("$.periodo.desde").value("2026-05-01"))
                .andExpect(jsonPath("$.periodo.hasta").value("2026-05-31"))
                .andExpect(jsonPath("$.ventas.total").value(33.0))
                .andExpect(jsonPath("$.ventas.pedidos").value(2))
                .andExpect(jsonPath("$.compras.total").value(20.0))
                .andExpect(jsonPath("$.ventasPorMes[11].mes").value("2026-05"));
    }

    @Test
    void sinVentasNoHayMargenYLosPeriodosSeCalculanBien() throws Exception {
        mockMvc.perform(get(URL).param("periodo", "ANIO"))
                .andExpect(jsonPath("$.periodo.desde").value("2026-01-01"))
                .andExpect(jsonPath("$.periodo.hasta").value("2026-06-15"))
                .andExpect(jsonPath("$.periodo.anteriorDesde").value("2025-01-01"))
                .andExpect(jsonPath("$.periodo.anteriorHasta").value("2025-06-15"))
                .andExpect(jsonPath("$.ventas.total").value(0.0))
                .andExpect(jsonPath("$.ganancia.margen").value(nullValue()));

        mockMvc.perform(get(URL).param("periodo", "RANGO").param("desde", "2026-06-01").param("hasta", "2026-06-10"))
                .andExpect(jsonPath("$.periodo.anteriorDesde").value("2026-05-22"))
                .andExpect(jsonPath("$.periodo.anteriorHasta").value("2026-05-31"));

        mockMvc.perform(get(URL).param("periodo", "RANGO").param("desde", "2026-06-10").param("hasta", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("no puede ser anterior")));
        mockMvc.perform(get(URL).param("periodo", "RANGO")).andExpect(status().isBadRequest());
        mockMvcSinSesion.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ más detalles

    @Test
    void detallesDeClientesProductosEInventario() throws Exception {
        Long jeringa = producto("Jeringa 5 ml", "10");
        Long guantes = producto("Guantes talla M", "20");
        Long gasa = producto("Gasa estéril", "4");
        Long venda = producto("Venda elástica", "3");
        Long tensiometro = producto("Tensiómetro", "50");
        compra(jeringa, "J1", null, 20, "10");
        compra(guantes, "G1", null, 5, "20");
        compra(gasa, "VENCIDO", "2026-04-30", 3, "4");                 // venció en abril
        Long compraVieja = compra(venda, "V1", null, 10, "3");        // llegó hace meses y no se vende
        jdbc.update("UPDATE lote SET fecha_ingreso = ? WHERE id = (SELECT lote_id FROM movimiento WHERE id = ?)",
                Timestamp.from(Instant.parse("2026-01-01T17:00:00Z")), compraVieja);
        jdbc.update("UPDATE producto SET costo = NULL WHERE id = ?", tensiometro);
        movimientos.registrarEntradaInicial(new EntradaRequest(tensiometro, null, null, 2, null, "Sin costo"));

        Long botica = cliente("Botica Grande");
        Long clinica = cliente("Clínica Chica");
        Long antigua = cliente("Farmacia Antigua");
        Long grande = pedido(botica, jeringa, 5);
        postJson("/api/pedidos/" + grande + "/items", """
                {"productoId": %d, "cantidad": 1}""".formatted(guantes));
        despachar(grande);                                             // 55 + 22 = 77
        despachar(pedido(clinica, jeringa, 1));                        // 11
        Long viejo = pedido(antigua, jeringa, 1);
        despachar(viejo);
        despachadoEl(viejo, "2026-03-01");                             // compró hace 106 días
        pedido(clinica, guantes, 2);                                   // borrador: 2 × 22 = 44

        mockMvc.perform(get(URL + "/detalles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productosMasVendidos[*].nombre", contains("Jeringa 5 ml", "Guantes talla M")))
                .andExpect(jsonPath("$.productosMasVendidos[0].unidades").value(6))
                .andExpect(jsonPath("$.productosMasVendidos[0].ventas").value(66.0))
                .andExpect(jsonPath("$.mejoresClientes[*].nombre", contains("Botica Grande", "Clínica Chica")))
                .andExpect(jsonPath("$.mejoresClientes[0].ventas").value(77.0))
                .andExpect(jsonPath("$.mejoresClientes[0].pedidos").value(1))
                .andExpect(jsonPath("$.diasInactivo").value(60))
                .andExpect(jsonPath("$.clientesInactivos[*].nombre", contains("Farmacia Antigua")))
                .andExpect(jsonPath("$.clientesInactivos[0].ultimoPedido").value("2026-03-01"))
                .andExpect(jsonPath("$.clientesInactivos[0].diasSinComprar").value(106))
                .andExpect(jsonPath("$.clientesInactivos[0].totalHistorico").value(11.0))
                // Vigente: 13 jeringas × 10 + 4 guantes × 20 + 10 vendas × 3 = 240 (+ 2 tensiómetros sin costo)
                .andExpect(jsonPath("$.inventario.valor").value(240.0))
                .andExpect(jsonPath("$.inventario.unidades").value(29))
                .andExpect(jsonPath("$.inventario.productosSinCosto").value(1))
                .andExpect(jsonPath("$.inventario.valorVencido").value(12.0))
                .andExpect(jsonPath("$.inventario.lotesVencidos").value(1))
                .andExpect(jsonPath("$.diasSinMovimiento").value(90))
                .andExpect(jsonPath("$.sinMovimiento[*].nombre", contains("Venda elástica")))
                .andExpect(jsonPath("$.sinMovimiento[0].stock").value(10))
                .andExpect(jsonPath("$.sinMovimiento[0].valor").value(30.0))
                .andExpect(jsonPath("$.sinMovimiento[0].ultimaSalida").value(nullValue()))
                .andExpect(jsonPath("$.pendiente.total").value(44.0))
                .andExpect(jsonPath("$.pendiente.pedidos").value(1))
                .andExpect(jsonPath("$.pendiente.saldos").value(0));
    }
}
