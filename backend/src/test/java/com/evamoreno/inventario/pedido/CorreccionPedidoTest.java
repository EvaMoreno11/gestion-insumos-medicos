package com.evamoreno.inventario.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

/** Corregir un pedido despachado. "Hoy" es el 15/06/2026 (reloj fijo de los tests). */
class CorreccionPedidoTest extends IntegracionTest {

    private static final String URL = "/api/pedidos";
    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);

    private int clientes;

    // ------------------------------------------------------------------ ayudas

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long producto(String nombre, String costo) throws Exception {
        return crear("/api/productos", """
                {"nombre": "%s", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "costo": %s}""".formatted(nombre, idCategoria("Otros"), costo));
    }

    private void entrada(Long productoId, String lote, LocalDate vence, int cantidad, String costo) throws Exception {
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "%s", "fechaVencimiento": "%s", "cantidad": %d, "costo": %s}"""
                .formatted(productoId, lote, vence, cantidad, costo)).andExpect(status().isCreated());
    }

    /** Pedido despachado para un cliente con 10 %: el producto de costo 10 sale a S/ 11. */
    private Long despachado(Long productoId, int cantidad) throws Exception {
        Long cliente = crear("/api/clientes", """
                {"nombre": "Botica %d", "porcentaje": 10}""".formatted(++clientes));
        Long pedido = crear(URL, """
                {"clienteId": %d}""".formatted(cliente));
        postJson(URL + "/" + pedido + "/items", """
                {"productoId": %d, "cantidad": %d}""".formatted(productoId, cantidad)).andExpect(status().isOk());
        return pedido;
    }

    private ResultActions despachar(Long pedido) throws Exception {
        return postJson(URL + "/" + pedido + "/despachar", "").andExpect(jsonPath("$.estado").value("DESPACHADO"));
    }

    private ResultActions corregir(Long pedido, String json) throws Exception {
        return postJson(URL + "/" + pedido + "/correcciones", json);
    }

    private static Long numero(ResultActions resultado, String ruta) throws Exception {
        return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta)).longValue();
    }

    // ------------------------------------------------------------------ devoluciones

    @Test
    void unaDevolucionVuelveAlLoteBajaElTotalYQuedaSaldoAFavor() throws Exception {
        Long jeringa = producto("Jeringa 5 ml", "10");
        entrada(jeringa, "L1", HOY.plusMonths(12), 20, "10");
        Long pedido = despachado(jeringa, 10);
        Long item = numero(despachar(pedido), "$.items[0].id");
        postJson(URL + "/" + pedido + "/pagos", """
                {"monto": 110, "medio": "EFECTIVO"}""").andExpect(jsonPath("$.cobro.estado").value("PAGADO"));

        corregir(pedido, """
                {"motivo": "Devolvieron 2: se entregó de más", "cambios": [{"itemId": %d, "cantidad": 8}]}"""
                .formatted(item))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cantidad").value(8))
                .andExpect(jsonPath("$.items[0].lotes[0].numeroLote").value("L1"))
                .andExpect(jsonPath("$.items[0].lotes[0].cantidad").value(8))
                .andExpect(jsonPath("$.total").value(88.0))
                .andExpect(jsonPath("$.cobro.estado").value("A_FAVOR"))
                .andExpect(jsonPath("$.cobro.saldo").value(-22.0))
                .andExpect(jsonPath("$.correcciones", hasSize(1)))
                .andExpect(jsonPath("$.correcciones[0].motivo").value("Devolvieron 2: se entregó de más"))
                .andExpect(jsonPath("$.correcciones[0].detalle[0]").value("Jeringa 5 ml: 10 → 8, vuelven 2 al lote L1"))
                .andExpect(jsonPath("$.correcciones[0].totalAnterior").value(110.0))
                .andExpect(jsonPath("$.correcciones[0].totalNuevo").value(88.0));

        // Las 2 unidades volvieron al lote, y el kárdex lo muestra como devolución
        mockMvc.perform(get("/api/productos/{id}", jeringa)).andExpect(jsonPath("$.stockDisponible").value(12));
        mockMvc.perform(get("/api/movimientos").param("productoId", jeringa.toString()))
                .andExpect(jsonPath("$.contenido[0].tipo").value("DEVOLUCION"))
                .andExpect(jsonPath("$.contenido[0].cantidad").value(2))
                .andExpect(jsonPath("$.contenido[0].costoUnitario").value(10.0))
                .andExpect(jsonPath("$.contenido[0].nota", containsString("Devolución · Corrección de la nota")));

        // La nota sale con la cantidad corregida
        byte[] xlsx = mockMvc.perform(get(URL + "/{id}/nota.xlsx", pedido)).andReturn().getResponse()
                .getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(9).getCell(6).getNumericCellValue()).isEqualTo(8);
        }

        // Cobranzas: pagó de más
        mockMvc.perform(get("/api/cobranzas"))
                .andExpect(jsonPath("$.totalPorCobrar").value(0.0))
                .andExpect(jsonPath("$.totalAFavor").value(22.0))
                .andExpect(jsonPath("$.aFavor[0].pedidoId").value(pedido.intValue()));

        // El resumen cuenta lo vendido y lo que costó, ya sin lo devuelto: 88 − 80
        mockMvc.perform(get("/api/resumen"))
                .andExpect(jsonPath("$.ventas.total").value(88.0))
                .andExpect(jsonPath("$.ganancia.costoVendido").value(80.0))
                .andExpect(jsonPath("$.ganancia.ganancia").value(8.0));
    }

    @Test
    void siSalioDeVariosLotesHayQueDecirAQueLoteVuelve() throws Exception {
        Long jeringa = producto("Jeringa 5 ml", "10");
        entrada(jeringa, "L-A", HOY.plusMonths(6), 3, "10");   // vence antes: sale primero
        entrada(jeringa, "L-B", HOY.plusMonths(18), 10, "10");
        Long pedido = despachado(jeringa, 5);
        ResultActions despacho = despachar(pedido)
                .andExpect(jsonPath("$.items[0].lotes[*].numeroLote", contains("L-A", "L-B")))
                .andExpect(jsonPath("$.items[0].lotes[*].cantidad", contains(3, 2)));
        Long item = numero(despacho, "$.items[0].id");
        Long loteA = numero(despacho, "$.items[0].lotes[0].loteId");
        Long loteB = numero(despacho, "$.items[0].lotes[1].loteId");

        corregir(pedido, """
                {"motivo": "Devolución", "cambios": [{"itemId": %d, "cantidad": 4}]}""".formatted(item))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("se entregó de 2 lotes: indica a qué lote")));
        corregir(pedido, """
                {"motivo": "Devolución", "cambios": [{"itemId": %d, "cantidad": 4,
                 "lotes": [{"loteId": %d, "cantidad": 2}]}]}""".formatted(item, loteA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("se devuelven 1 unidades, pero en los lotes suman 2")));
        corregir(pedido, """
                {"motivo": "Devolución", "cambios": [{"itemId": %d, "cantidad": 0,
                 "lotes": [{"loteId": %d, "cantidad": 5}]}]}""".formatted(item, loteB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("se entregaron 2")));

        corregir(pedido, """
                {"motivo": "Devolvieron 1 caja del lote L-A", "cambios": [{"itemId": %d, "cantidad": 4,
                 "lotes": [{"loteId": %d, "cantidad": 1}]}]}""".formatted(item, loteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lotes[*].numeroLote", contains("L-A", "L-B")))
                .andExpect(jsonPath("$.items[0].lotes[*].cantidad", contains(2, 2)))
                .andExpect(jsonPath("$.correcciones[0].detalle[0]").value("Jeringa 5 ml: 5 → 4, vuelven 1 al lote L-A"));
    }

    // ------------------------------------------------------------------ otros cambios

    @Test
    void subirCantidadCambiarPrecioAgregarYQuitarItems() throws Exception {
        Long jeringa = producto("Jeringa 5 ml", "10");
        Long guantes = producto("Guantes M", "20");
        entrada(jeringa, "J1", HOY.plusMonths(12), 20, "10");
        entrada(guantes, "G1", HOY.plusMonths(12), 5, "20");
        Long pedido = despachado(jeringa, 5);
        postJson(URL + "/" + pedido + "/items", """
                {"descripcion": "Termómetro digital", "cantidad": 1, "precio": 30}""").andExpect(status().isOk());
        ResultActions despacho = despachar(pedido).andExpect(jsonPath("$.total").value(85.0));
        Long itemJeringa = numero(despacho, "$.items[0].id");
        Long itemTermometro = numero(despacho, "$.items[1].id");

        // Un borrador no se "corrige"; una corrección sin motivo o sin cambios no se guarda
        Long borrador = despachado(jeringa, 1);
        corregir(borrador, """
                {"motivo": "x", "cambios": []}""").andExpect(status().isConflict());
        corregir(pedido, """
                {"motivo": " ", "cambios": [{"itemId": %d, "cantidad": 6}]}""".formatted(itemJeringa))
                .andExpect(status().isBadRequest());
        corregir(pedido, """
                {"motivo": "Nada", "cambios": [{"itemId": %d, "cantidad": 5}]}""".formatted(itemJeringa))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No hay cambios que guardar"));

        corregir(pedido, """
                {"motivo": "Pidió 2 más, precio especial y guantes; el termómetro no llegó",
                 "cambios": [{"itemId": %d, "cantidad": 7, "precio": 10.50}, {"itemId": %d, "cantidad": 0}],
                 "nuevos": [{"productoId": %d, "cantidad": 2}]}""".formatted(itemJeringa, itemTermometro, guantes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].descripcion", contains("Jeringa 5 ml", "Guantes M")))
                .andExpect(jsonPath("$.items[0].cantidad").value(7))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(10.5))
                .andExpect(jsonPath("$.items[1].precioUnitario").value(22.0))
                .andExpect(jsonPath("$.total").value(117.5))    // 7 × 10.50 + 2 × 22
                .andExpect(jsonPath("$.correcciones[0].detalle", contains(
                        "Jeringa 5 ml: 5 → 7 (se entregan 2 más)",
                        "Jeringa 5 ml: precio S/ 11.00 → S/ 10.50",
                        "Termómetro digital: 1 → 0 (quitado)",
                        "Agregado: Guantes M × 2 a S/ 22.00")));
        mockMvc.perform(get("/api/productos/{id}", jeringa)).andExpect(jsonPath("$.stockDisponible").value(13));
        mockMvc.perform(get("/api/productos/{id}", guantes)).andExpect(jsonPath("$.stockDisponible").value(3));

        // Quitar un producto del inventario: devuelve todo y queda en 0 (no sale en la nota)
        Long itemGuantes = numero(mockMvc.perform(get(URL + "/{id}", pedido)), "$.items[1].id");
        corregir(pedido, """
                {"motivo": "Devolvieron los guantes", "cambios": [{"itemId": %d, "cantidad": 0}]}"""
                .formatted(itemGuantes))
                .andExpect(jsonPath("$.items[1].cantidad").value(0))
                .andExpect(jsonPath("$.items[1].lotes", hasSize(0)))
                .andExpect(jsonPath("$.total").value(73.5))
                .andExpect(jsonPath("$.correcciones", hasSize(2)));
        mockMvc.perform(get("/api/productos/{id}", guantes)).andExpect(jsonPath("$.stockDisponible").value(5));
        byte[] xlsx = mockMvc.perform(get(URL + "/{id}/nota.xlsx", pedido)).andReturn().getResponse()
                .getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(9).getCell(1).getStringCellValue()).isEqualTo("Jeringa 5 ml");
            assertThat(hoja.getRow(10).getCell(5).getStringCellValue()).isEqualTo("TOTAL S/"); // sin los guantes
        }
        mockMvc.perform(get(URL).param("estado", "DESPACHADO"))
                .andExpect(jsonPath("$.contenido[0].cantidadItems").value(1));

        // Sin stock no se puede agregar (en la app real la transacción se deshace entera; aquí va al final
        // porque el test comparte una sola transacción)
        Long otro = producto("Gasa estéril", "5");
        corregir(pedido, """
                {"motivo": "Sin stock", "nuevos": [{"productoId": %d, "cantidad": 50}]}""".formatted(otro))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Stock insuficiente")));
    }
}
