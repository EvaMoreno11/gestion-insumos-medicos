package com.evamoreno.inventario.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

/** "Hoy" es el 15/06/2026 (reloj fijo de los tests). */
class PedidoControllerTest extends IntegracionTest {

    private static final String URL = "/api/pedidos";
    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);

    // ------------------------------------------------------------------ ayudas

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static Long id(ResultActions resultado, String ruta) throws Exception {
        return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta)).longValue();
    }

    private Long producto(String nombre, String costo) throws Exception {
        return crear("/api/productos", """
                {"nombre": "%s", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "costo": %s}""".formatted(nombre, idCategoria("Otros"), costo));
    }

    /** Entrada con el costo que ya tiene el producto (así no cambian los precios que se esperan). */
    private void entrada(Long productoId, String lote, LocalDate vence, int cantidad) throws Exception {
        Object costo = JsonPath.read(mockMvc.perform(get("/api/productos/{id}", productoId)).andReturn()
                .getResponse().getContentAsString(), "$.costo");
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "%s", "fechaVencimiento": "%s", "cantidad": %d, "costo": %s}"""
                .formatted(productoId, lote, vence, cantidad, costo)).andExpect(status().isCreated());
    }

    private Long cliente(String nombre, String porcentaje) throws Exception {
        return crear("/api/clientes", """
                {"nombre": "%s", "porcentaje": %s,
                 "locales": [{"direccion": "Av. Los Olivos 123", "contacto": "Dra. Ana", "telefono": "987654321"}]}"""
                .formatted(nombre, porcentaje));
    }

    private Long pedido(Long clienteId) throws Exception {
        return crear(URL, """
                {"clienteId": %d}""".formatted(clienteId));
    }

    private ResultActions agregar(Long pedidoId, Long productoId, int cantidad) throws Exception {
        return postJson(URL + "/" + pedidoId + "/items", """
                {"productoId": %d, "cantidad": %d}""".formatted(productoId, cantidad));
    }

    // ------------------------------------------------------------------ crear y precios

    @Test
    void usaElPorcentajeDelClienteYCalculaPreciosYTotal() throws Exception {
        Long aerocamara = producto("Aerocámara pediátrica", "4.80");
        Long pedido = pedido(cliente("Botica Demo", "15"));

        mockMvc.perform(get(URL + "/{id}", pedido))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.numero").value(nullValue()))
                .andExpect(jsonPath("$.cliente").value("Botica Demo"))
                .andExpect(jsonPath("$.direccion").value("Av. Los Olivos 123"))
                .andExpect(jsonPath("$.categoria").value("B"))
                .andExpect(jsonPath("$.total").value(0.0));

        agregar(pedido, aerocamara, 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].descripcion").value("Aerocámara pediátrica"))
                .andExpect(jsonPath("$.items[0].presentacion").value("Caja x 100"))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(5.52)) // 4.80 + 15 %
                .andExpect(jsonPath("$.items[0].precioManual").value(false))
                .andExpect(jsonPath("$.items[0].total").value(27.6))
                .andExpect(jsonPath("$.total").value(27.6));

        // Ítem a mano: algo que no está en el catálogo
        postJson(URL + "/" + pedido + "/items", """
                {"descripcion": " Tensiómetro  digital ", "presentacion": "Unidad", "cantidad": 2, "precio": 10}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].descripcion").value("Tensiómetro digital"))
                .andExpect(jsonPath("$.items[1].productoId").value(nullValue()))
                .andExpect(jsonPath("$.items[1].disponibilidad").value("NO_REGISTRADO"))
                .andExpect(jsonPath("$.total").value(47.6));
    }

    @Test
    void elPrimerPedidoDeUnClienteSinPorcentajeLoPideYLoGuarda() throws Exception {
        Long cliente = crear("/api/clientes", """
                {"nombre": "Consultorio Dr. Pérez"}""");

        postJson(URL, """
                {"clienteId": %d}""".formatted(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("aún no tiene porcentaje")));

        postJson(URL, """
                {"clienteId": %d, "porcentaje": 20}""".formatted(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria").value("C"));

        // Quedó guardado: el siguiente pedido ya no lo pide
        mockMvc.perform(get("/api/clientes/{id}", cliente)).andExpect(jsonPath("$.categoria").value("C"));
        postJson(URL, """
                {"clienteId": %d}""".formatted(cliente)).andExpect(status().isCreated());
    }

    @Test
    void siElClienteTieneVariosLocalesHayQueElegirUno() throws Exception {
        ResultActions creado = postJson("/api/clientes", """
                {"nombre": "Botica Demo", "porcentaje": 10,
                 "locales": [{"nombre": "Local 1", "direccion": "Dirección 1"}, {"nombre": "Local 2", "direccion": "Dirección 2"}]}""");
        Long cliente = id(creado, "$.id");
        Long local2 = id(creado, "$.locales[1].id");

        postJson(URL, """
                {"clienteId": %d}""".formatted(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("tiene 2 locales")));

        postJson(URL, """
                {"clienteId": %d, "localId": %d}""".formatted(cliente, local2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.local").value("Local 2"))
                .andExpect(jsonPath("$.direccion").value("Dirección 2"));

        postJson(URL, """
                {"clienteId": %d, "localId": 999999}""".formatted(cliente)).andExpect(status().isNotFound());
    }

    @Test
    void cambiarElPorcentajeRecalculaLosPreciosSalvoLosEscritosAMano() throws Exception {
        Long a = producto("Producto A", "10");
        Long b = producto("Producto B", "10");
        Long cliente = cliente("Botica Demo", "10");
        Long pedido = pedido(cliente);
        agregar(pedido, a, 1).andExpect(jsonPath("$.items[0].precioUnitario").value(11.0));
        Long itemB = id(postJson(URL + "/" + pedido + "/items", """
                {"productoId": %d, "cantidad": 2, "precio": 12.50}""".formatted(b)), "$.items[1].id");

        putJson(URL + "/" + pedido, """
                {"porcentaje": 20, "guardarPorcentajeEnCliente": true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("C"))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(12.0))   // recalculado
                .andExpect(jsonPath("$.items[1].precioUnitario").value(12.5))   // a mano: se respeta
                .andExpect(jsonPath("$.items[1].precioManual").value(true))
                .andExpect(jsonPath("$.total").value(37.0));
        mockMvc.perform(get("/api/clientes/{id}", cliente)).andExpect(jsonPath("$.porcentaje").value(20));

        // Volver al precio calculado
        putJson(URL + "/" + pedido + "/items/" + itemB, """
                {"cantidad": 3, "precioAutomatico": true}""")
                .andExpect(jsonPath("$.items[1].cantidad").value(3))
                .andExpect(jsonPath("$.items[1].precioUnitario").value(12.0))
                .andExpect(jsonPath("$.items[1].precioManual").value(false));
    }

    @Test
    void unProductoSinCostoQuedaSinPrecioHastaQueSeEscribe() throws Exception {
        Long sinCosto = crear("/api/productos", """
                {"nombre": "Producto sin costo", "marca": "Marca A", "categoriaId": %d, "presentacion": "Unidad",
                 "unidadMedida": "Unidad"}""".formatted(idCategoria("Otros")));
        Long pedido = pedido(cliente("Botica Demo", "15"));

        Long item = id(agregar(pedido, sinCosto, 2)
                .andExpect(jsonPath("$.items[0].precioUnitario").value(nullValue()))
                .andExpect(jsonPath("$.itemsSinPrecio").value(1))
                .andExpect(jsonPath("$.listoParaDespachar").value(false)), "$.items[0].id");

        postJson(URL + "/" + pedido + "/despachar", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Falta el precio de: Producto sin costo"));

        putJson(URL + "/" + pedido + "/items/" + item, """
                {"cantidad": 2, "precio": 3.5}""")
                .andExpect(jsonPath("$.items[0].total").value(7.0))
                .andExpect(jsonPath("$.itemsSinPrecio").value(0));

        // Cuando llega con su costo, el producto lo toma y los ítems nuevos ya salen con precio
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "L1", "cantidad": 5, "costo": 2}""".formatted(sinCosto))
                .andExpect(status().isCreated());
        mockMvc.perform(get(URL + "/{id}", pedido))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(3.5)) // el escrito a mano se respeta
                .andExpect(jsonPath("$.listoParaDespachar").value(true));
        Long otro = pedido(cliente("Clínica Y", "15"));
        agregar(otro, sinCosto, 1).andExpect(jsonPath("$.items[0].precioUnitario").value(2.3)); // 2 + 15 %
    }

    @Test
    void noRepiteUnProductoYPermiteQuitarItems() throws Exception {
        Long a = producto("Producto A", "10");
        Long pedido = pedido(cliente("Botica Demo", "10"));
        Long item = id(agregar(pedido, a, 1), "$.items[0].id");

        agregar(pedido, a, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("ya está en el pedido")));
        postJson(URL + "/" + pedido + "/items", """
                {"cantidad": 1}""").andExpect(status().isBadRequest()); // ni producto ni descripción

        mockMvc.perform(delete(URL + "/{id}/items/{item}", pedido, item))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", empty()))
                .andExpect(jsonPath("$.total").value(0.0));
        mockMvc.perform(delete(URL + "/{id}/items/{item}", pedido, item)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ faltantes y prioridad

    @Test
    void permitePedirMasDeLoQueHayYAvisaCuantoFalta() throws Exception {
        Long guantes = producto("Guantes talla M", "10");
        entrada(guantes, "G1", HOY.plusMonths(6), 3);
        entrada(guantes, "VENCIDO", HOY.minusMonths(1), 50); // lo vencido no cuenta
        Long pedido = pedido(cliente("Botica Demo", "10"));

        agregar(pedido, guantes, 5)
                .andExpect(jsonPath("$.items[0].disponibilidad").value("PARCIAL"))
                .andExpect(jsonPath("$.items[0].disponible").value(3))
                .andExpect(jsonPath("$.items[0].faltante").value(2))
                .andExpect(jsonPath("$.total").value(55.0)) // el total cuenta las 5 pedidas
                .andExpect(jsonPath("$.itemsConFaltante").value(1))
                .andExpect(jsonPath("$.listoParaDespachar").value(false));

        postJson(URL + "/" + pedido + "/despachar", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "No se puede despachar: faltan unidades de Guantes talla M (faltan 2). Para entregar lo que hay "
                                + "y dejar el resto pendiente, confirma la entrega parcial"));

        // Llega la mercadería: el mismo borrador pasa a estar completo
        entrada(guantes, "G2", HOY.plusMonths(12), 10);
        mockMvc.perform(get(URL + "/{id}", pedido))
                .andExpect(jsonPath("$.items[0].disponibilidad").value("DISPONIBLE"))
                .andExpect(jsonPath("$.listoParaDespachar").value(true));
    }

    @Test
    void elStockSeSeparaParaElPedidoMasAntiguo() throws Exception {
        Long guantes = producto("Guantes talla M", "10");
        entrada(guantes, "G1", HOY.plusMonths(6), 10);
        Long farmacia = pedido(cliente("Farmacia X", "10"));
        Long clinica = pedido(cliente("Clínica Y", "10"));
        agregar(farmacia, guantes, 8).andExpect(jsonPath("$.items[0].disponibilidad").value("DISPONIBLE"));

        // Hay 10 y la farmacia separó 8: a la clínica solo le quedan 2
        agregar(clinica, guantes, 6)
                .andExpect(jsonPath("$.items[0].disponibilidad").value("PARCIAL"))
                .andExpect(jsonPath("$.items[0].disponible").value(2))
                .andExpect(jsonPath("$.items[0].faltante").value(4));
        mockMvc.perform(get(URL + "/{id}", farmacia))
                .andExpect(jsonPath("$.items[0].disponibilidad").value("DISPONIBLE"));

        // El inventario muestra: hay 10 · separadas 10 · libres 0
        mockMvc.perform(get("/api/productos/{id}", guantes))
                .andExpect(jsonPath("$.stockDisponible").value(10))
                .andExpect(jsonPath("$.stockSeparado").value(10))
                .andExpect(jsonPath("$.stockLibre").value(0));

        // "Por comprar": faltan 4 en total, y los espera la clínica
        mockMvc.perform(get(URL + "/por-comprar"))
                .andExpect(jsonPath("$.productos", hasSize(1)))
                .andExpect(jsonPath("$.productos[0].nombre").value("Guantes talla M"))
                .andExpect(jsonPath("$.productos[0].stockDisponible").value(10))
                .andExpect(jsonPath("$.productos[0].pedido").value(14))
                .andExpect(jsonPath("$.productos[0].faltante").value(4))
                .andExpect(jsonPath("$.productos[0].pedidos[*].cliente", contains("Clínica Y")))
                .andExpect(jsonPath("$.productos[0].pedidos[0].faltante").value(4))
                .andExpect(jsonPath("$.costoTotal").value(40.0))   // faltan 4 × costo 10
                .andExpect(jsonPath("$.productosSinCosto").value(0));

        mockMvc.perform(get(URL).param("estado", "BORRADOR"))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].cliente").value("Clínica Y"))  // más reciente primero
                .andExpect(jsonPath("$.contenido[0].itemsConFaltante").value(1))
                .andExpect(jsonPath("$.contenido[1].itemsConFaltante").value(0));

        // Si la farmacia anula su pedido, lo separado pasa a la clínica
        postJson(URL + "/" + farmacia + "/anular", "").andExpect(jsonPath("$.estado").value("ANULADO"));
        mockMvc.perform(get(URL + "/{id}", clinica))
                .andExpect(jsonPath("$.items[0].disponibilidad").value("DISPONIBLE"));
        mockMvc.perform(get("/api/productos/{id}", guantes))
                .andExpect(jsonPath("$.stockSeparado").value(6))
                .andExpect(jsonPath("$.stockLibre").value(4));
        mockMvc.perform(get(URL + "/por-comprar")).andExpect(jsonPath("$.productos", empty()));
    }

    @Test
    void porComprarSumaCuantoCostariaLoQueFaltaYCuentaLosProductosSinCosto() throws Exception {
        Long jeringas = producto("Jeringa 10 ml", "2.35");
        Long tensiometro = producto("Tensiómetro", "null");
        Long pedido = pedido(cliente("Botica Costos", "10"));
        agregar(pedido, jeringas, 3).andExpect(jsonPath("$.items", hasSize(1)));
        agregar(pedido, tensiometro, 1).andExpect(jsonPath("$.items", hasSize(2)));
        postJson(URL + "/" + pedido + "/items", """
                {"descripcion": "Termómetro digital", "marca": "Marca Z", "cantidad": 5}""")
                .andExpect(jsonPath("$.items", hasSize(3)));

        // Sin stock: faltan 3 jeringas × 2.35 = 7.05; el tensiómetro no tiene costo y el ítem a mano tampoco
        mockMvc.perform(get(URL + "/por-comprar"))
                .andExpect(jsonPath("$.productos", hasSize(2)))
                .andExpect(jsonPath("$.costoTotal").value(7.05))
                .andExpect(jsonPath("$.productosSinCosto").value(1))
                .andExpect(jsonPath("$.noRegistrados", hasSize(1)));
    }

    @Test
    void losItemsAManoAparecenEnPorComprarComoNoRegistrados() throws Exception {
        Long pedido = pedido(cliente("Botica Demo", "10"));
        postJson(URL + "/" + pedido + "/items", """
                {"descripcion": "Tensiómetro digital", "marca": "Marca Z", "cantidad": 2}""")
                .andExpect(jsonPath("$.items[0].precioUnitario").value(nullValue()));

        mockMvc.perform(get(URL + "/por-comprar"))
                .andExpect(jsonPath("$.productos", empty()))
                .andExpect(jsonPath("$.noRegistrados[0].descripcion").value("Tensiómetro digital"))
                .andExpect(jsonPath("$.noRegistrados[0].cliente").value("Botica Demo"))
                .andExpect(jsonPath("$.noRegistrados[0].cantidad").value(2));
    }

    // ------------------------------------------------------------------ despacho y nota

    @Test
    void unTotalGrandeSeVeCompletoEnLaNotaSinPartirseEnDosLineas() throws Exception {
        Long pedido = pedido(cliente("Clínica Grande", "15"));
        postJson(URL + "/" + pedido + "/items", """
                {"descripcion": "Equipo de prueba", "presentacion": "Unidad", "cantidad": 50, "precio": 2450}""")
                .andExpect(jsonPath("$.total").value(122500.0));

        // PDF: el monto aparece entero dos veces (total de la línea y total de la nota); si se partiera en dos
        // líneas, el texto saldría cortado ("122,500.0" y "0")
        byte[] pdf = mockMvc.perform(get(URL + "/{id}/nota.pdf", pedido)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (PdfReader lector = new PdfReader(pdf)) {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertThat(texto.split(java.util.regex.Pattern.quote("122,500.00"), -1)).hasSize(3);
            assertThat(texto).contains("TOTAL S/", "2,450.00");
        }

        // Excel: el total va en una celda unida (Precio + Total), con espacio para el número completo
        byte[] xlsx = mockMvc.perform(get(URL + "/{id}/nota.xlsx", pedido)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(0);
            Row total = null;
            for (Row fila : hoja) {
                if (fila.getCell(5) != null && "TOTAL S/".equals(fila.getCell(5).toString())) {
                    total = fila;
                }
            }
            assertThat(total).as("fila del total").isNotNull();
            int filaTotal = total.getRowNum();
            assertThat(total.getCell(7).getNumericCellValue()).isCloseTo(122500, within(0.001));
            assertThat(hoja.getMergedRegions()).anySatisfy(region -> {
                assertThat(region.getFirstRow()).isEqualTo(filaTotal);
                assertThat(region.getFirstColumn()).isEqualTo(7);
                assertThat(region.getLastColumn()).isEqualTo(8);
            });
        }
    }

    @Test
    void despachaPorLotesAsignaElNumeroYGeneraLaNota() throws Exception {
        putJson("/api/negocio", """
                {"nombre": "Distribuidora Demo", "direccion": "Av. Principal 123, Lima", "telefono": "999 888 777"}""")
                .andExpect(status().isOk());
        putJson(URL + "/numeracion", """
                {"siguiente": 7323}""").andExpect(jsonPath("$.proximoNumero").value("0001-007323"));

        Long jeringas = producto("Jeringa 5 ml", "8.50");
        entrada(jeringas, "J-NUEVO", HOY.plusMonths(20), 10);
        entrada(jeringas, "J-VIEJO", HOY.plusMonths(1), 3); // vence antes: sale primero
        Long pedido = pedido(cliente("Botica Demo", "15"));
        agregar(pedido, jeringas, 5).andExpect(jsonPath("$.listoParaDespachar").value(true));
        postJson(URL + "/" + pedido + "/items", """
                {"descripcion": "Tensiómetro digital", "presentacion": "Unidad", "cantidad": 1, "precio": 40}""");

        // Nota de un borrador: sin número ni lotes
        byte[] borrador = mockMvc.perform(get(URL + "/{id}/nota.xlsx", pedido)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(borrador))) {
            assertThat(libro.getSheetAt(0).getRow(1).getCell(6).getStringCellValue()).isEqualTo("BORRADOR");
        }

        postJson(URL + "/" + pedido + "/despachar", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESPACHADO"))
                .andExpect(jsonPath("$.numero").value("0001-007323"))
                .andExpect(jsonPath("$.items[0].disponibilidad").value("DESPACHADO"))
                .andExpect(jsonPath("$.items[0].lotes[*].numeroLote", contains("J-VIEJO", "J-NUEVO")))
                .andExpect(jsonPath("$.items[0].lotes[*].cantidad", contains(3, 2)))
                .andExpect(jsonPath("$.items[1].lotes", empty()))
                .andExpect(jsonPath("$.total").value(88.9)); // 5 × 9.78 + 40

        // Se descontó el stock y el historial indica la nota
        mockMvc.perform(get("/api/productos/{id}", jeringas))
                .andExpect(jsonPath("$.stockDisponible").value(8))
                .andExpect(jsonPath("$.stockSeparado").value(0));
        mockMvc.perform(get("/api/movimientos").param("productoId", jeringas.toString()))
                .andExpect(jsonPath("$.contenido[0].nota").value("Nota de pedido 0001-007323"));
        mockMvc.perform(get(URL + "/numeracion")).andExpect(jsonPath("$.proximoNumero").value("0001-007324"));

        // Un pedido despachado ya no se modifica
        agregar(pedido, jeringas, 1).andExpect(status().isConflict());
        postJson(URL + "/" + pedido + "/anular", "").andExpect(status().isConflict());

        // ---- Excel: una fila por lote
        byte[] xlsx = mockMvc.perform(get(URL + "/{id}/nota.xlsx", pedido))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("nota-pedido-0001-007323.xlsx")))
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Distribuidora Demo");
            assertThat(hoja.getRow(1).getCell(6).getStringCellValue()).isEqualTo("N° 0001-007323");
            assertThat(hoja.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Cliente:  Botica Demo");

            Row lote1 = hoja.getRow(9);   // fila 10 de Excel: primer lote del ítem 1
            assertThat(lote1.getCell(0).getNumericCellValue()).isEqualTo(1);
            assertThat(lote1.getCell(1).getStringCellValue()).isEqualTo("Jeringa 5 ml");
            assertThat(lote1.getCell(2).getStringCellValue()).isEqualTo("Caja x 100");
            assertThat(lote1.getCell(4).getStringCellValue()).isEqualTo("J-VIEJO");
            assertThat(lote1.getCell(5).getStringCellValue()).isEqualTo("07/2026");
            assertThat(lote1.getCell(6).getNumericCellValue()).isEqualTo(3);
            assertThat(lote1.getCell(7).getNumericCellValue()).isEqualTo(9.78);
            assertThat(lote1.getCell(8).getNumericCellValue()).isCloseTo(29.34, within(0.001)); // fórmula ya calculada

            Row lote2 = hoja.getRow(10);  // segundo lote del mismo ítem: sin número
            assertThat(lote2.getCell(0).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
            assertThat(lote2.getCell(4).getStringCellValue()).isEqualTo("J-NUEVO");
            assertThat(lote2.getCell(6).getNumericCellValue()).isEqualTo(2);

            Row manual = hoja.getRow(11);
            assertThat(manual.getCell(0).getNumericCellValue()).isEqualTo(2);
            assertThat(manual.getCell(1).getStringCellValue()).isEqualTo("Tensiómetro digital");

            Row total = hoja.getRow(12);
            assertThat(total.getCell(5).getStringCellValue()).isEqualTo("TOTAL S/");
            assertThat(total.getCell(7).getNumericCellValue()).isCloseTo(88.9, within(0.001)); // en Precio + Total
        }

        // ---- PDF
        byte[] pdf = mockMvc.perform(get(URL + "/{id}/nota.pdf", pedido))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                // Se abre en el navegador (para verlo e imprimirlo), con su nombre para guardarlo
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andExpect(header().string("Content-Disposition", containsString("nota-pedido-0001-007323.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        try (PdfReader lector = new PdfReader(pdf)) {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertThat(texto).contains("Distribuidora Demo", "NOTA DE PEDIDO", "0001-007323", "Botica Demo",
                    "Jeringa 5 ml", "J-VIEJO", "07/2026", "Tensiómetro digital", "TOTAL S/", "88.90");
            // Los datos internos nunca van en la nota
            assertThat(texto).doesNotContain("8.50", "15 %", "Categoría");
        }
    }

    @Test
    void laNotaMuestraElRucODniDelClienteDebajoDeLaFechaSinMoverLaTabla() throws Exception {
        Long jeringas = producto("Jeringa 5 ml", "8.50");
        entrada(jeringas, "J1", HOY.plusMonths(12), 10);
        Long empresa = crear("/api/clientes", """
                {"nombre": "Botica Empresa", "rucDni": "20601234567", "porcentaje": 15,
                 "locales": [{"direccion": "Av. Uno 1", "contacto": "Dra. Ana", "telefono": "987654321"}]}""");
        Long persona = crear("/api/clientes", """
                {"nombre": "Clínica Persona", "rucDni": "45678912", "porcentaje": 15}""");
        Long conRuc = pedido(empresa);
        agregar(conRuc, jeringas, 2);
        Long conDni = pedido(persona);
        agregar(conDni, jeringas, 1);

        byte[] xlsx = mockMvc.perform(get(URL + "/{id}/nota.xlsx", conRuc)).andReturn().getResponse()
                .getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(3).getCell(4).getStringCellValue()).startsWith("Fecha:");
            assertThat(hoja.getRow(4).getCell(4).getStringCellValue()).isEqualTo("RUC:  20601234567");
            assertThat(hoja.getRow(5).getCell(4).getStringCellValue()).isEqualTo("Contacto:  Dra. Ana · 987654321");
            assertThat(hoja.getRow(9).getCell(1).getStringCellValue()).isEqualTo("Jeringa 5 ml"); // la tabla no se mueve
        }
        byte[] pdf = mockMvc.perform(get(URL + "/{id}/nota.pdf", conRuc)).andReturn().getResponse()
                .getContentAsByteArray();
        try (PdfReader lector = new PdfReader(pdf)) {
            assertThat(new PdfTextExtractor(lector).getTextFromPage(1)).contains("RUC:", "20601234567");
        }

        // Con 8 dígitos es un DNI; sin documento, no sale nada
        byte[] conDniPdf = mockMvc.perform(get(URL + "/{id}/nota.pdf", conDni)).andReturn().getResponse()
                .getContentAsByteArray();
        try (PdfReader lector = new PdfReader(conDniPdf)) {
            assertThat(new PdfTextExtractor(lector).getTextFromPage(1)).contains("DNI:", "45678912").doesNotContain("RUC");
        }
        Long sinDocumento = pedido(cliente("Botica Tres Hermanos", "15"));
        agregar(sinDocumento, jeringas, 1);
        byte[] sinDocumentoPdf = mockMvc.perform(get(URL + "/{id}/nota.pdf", sinDocumento)).andReturn().getResponse()
                .getContentAsByteArray();
        try (PdfReader lector = new PdfReader(sinDocumentoPdf)) {
            assertThat(new PdfTextExtractor(lector).getTextFromPage(1)).doesNotContain("RUC", "DNI");
        }
    }

    @Test
    void unPedidoVacioNoSeDespachaYUnoAnuladoNoTieneNota() throws Exception {
        Long pedido = pedido(cliente("Botica Demo", "10"));

        postJson(URL + "/" + pedido + "/despachar", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El pedido no tiene ítems"));

        postJson(URL + "/" + pedido + "/anular", "").andExpect(status().isOk());
        mockMvc.perform(get(URL + "/{id}/nota.pdf", pedido)).andExpect(status().isConflict());
        mockMvc.perform(get(URL + "/{id}", 999999)).andExpect(status().isNotFound());
    }
}
