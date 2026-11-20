package com.evamoreno.inventario.pedido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

/**
 * Entregas parciales (lo que falta pasa a un saldo), pedidos "Solo tabla" e ítems a mano
 * que pasan a ser productos. "Hoy" es el 15/06/2026 (reloj fijo de los tests).
 */
class EntregaParcialTest extends IntegracionTest {

    private static final String URL = "/api/pedidos";
    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);
    private static final String PARCIAL = """
            {"entregarLoDisponible": true}""";

    // ------------------------------------------------------------------ ayudas

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long producto(String nombre, String costo) throws Exception {
        return crear("/api/productos", """
                {"nombre": "%s", "marca": "Marca A", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "costo": %s}""".formatted(nombre, idCategoria("Otros"), costo));
    }

    /** Entrada con el costo que ya tiene el producto (así no cambian los precios que se esperan). */
    private void entrada(Long productoId, String lote, int cantidad) throws Exception {
        Object costo = JsonPath.read(mockMvc.perform(get("/api/productos/{id}", productoId)).andReturn()
                .getResponse().getContentAsString(), "$.costo");
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "%s", "fechaVencimiento": "%s", "cantidad": %d, "costo": %s}"""
                .formatted(productoId, lote, HOY.plusMonths(12), cantidad, costo)).andExpect(status().isCreated());
    }

    private Long cliente(String nombre) throws Exception {
        return crear("/api/clientes", """
                {"nombre": "%s", "porcentaje": 10, "locales": [{"direccion": "Av. Los Olivos 123"}]}"""
                .formatted(nombre));
    }

    private Long pedido(Long clienteId, String documento) throws Exception {
        return crear(URL, """
                {"clienteId": %d, "documento": "%s", "nota": "(No se factura)"}""".formatted(clienteId, documento));
    }

    private ResultActions agregar(Long pedidoId, Long productoId, int cantidad) throws Exception {
        return postJson(URL + "/" + pedidoId + "/items", """
                {"productoId": %d, "cantidad": %d}""".formatted(productoId, cantidad));
    }

    private static Long numero(ResultActions resultado, String ruta) throws Exception {
        return ((Number) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta)).longValue();
    }

    // ------------------------------------------------------------------ entrega parcial

    @Test
    void entregaLoQueHayYLoQueFaltaPasaAUnSaldoConLosMismosPrecios() throws Exception {
        putJson(URL + "/numeracion", """
                {"siguiente": 120}""").andExpect(status().isOk());
        Long jeringas = producto("Jeringa 5 ml", "10");
        Long guantes = producto("Guantes talla M", "20");
        Long gasa = producto("Gasa estéril", "5");
        entrada(jeringas, "J1", 10);
        entrada(guantes, "G1", 3);
        Long pedido = pedido(cliente("Botica Santa Rosa"), "NOTA");
        agregar(pedido, jeringas, 10);                                   // completo
        agregar(pedido, guantes, 5);                                     // hay 3 de 5
        agregar(pedido, gasa, 4)                                         // no hay nada
                .andExpect(jsonPath("$.listoParaDespachar").value(false))
                .andExpect(jsonPath("$.puedeEntregarParcial").value(true));
        Long itemGuantes = numero(mockMvc.perform(get(URL + "/{id}", pedido)), "$.items[1].id");
        putJson(URL + "/" + pedido + "/items/" + itemGuantes, """
                {"cantidad": 5, "precio": 25}""").andExpect(status().isOk()); // precio escrito a mano

        // Sin confirmar la entrega parcial no se despacha
        postJson(URL + "/" + pedido + "/despachar", "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("confirma la entrega parcial")));

        // Se entrega lo que hay: la nota y el total son solo lo entregado
        ResultActions despacho = postJson(URL + "/" + pedido + "/despachar", PARCIAL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESPACHADO"))
                .andExpect(jsonPath("$.numero").value("0001-000120"))
                .andExpect(jsonPath("$.items[*].descripcion", contains("Jeringa 5 ml", "Guantes talla M")))
                .andExpect(jsonPath("$.items[*].cantidad", contains(10, 3)))
                .andExpect(jsonPath("$.total").value(185.0))                 // 10 × 11 + 3 × 25
                .andExpect(jsonPath("$.grupo.entregado").value(185.0))
                .andExpect(jsonPath("$.grupo.pendiente").value(72.0))        // 2 × 25 + 4 × 5.50
                .andExpect(jsonPath("$.grupo.pedido").value(257.0))
                .andExpect(jsonPath("$.grupo.pedidos", hasSize(2)));
        Long saldo = numero(despacho, "$.grupo.pedidos[1].id");

        // El saldo: mismo cliente, observaciones y precios (también el escrito a mano), con lo que falta
        mockMvc.perform(get(URL + "/{id}", saldo))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.cliente").value("Botica Santa Rosa"))
                .andExpect(jsonPath("$.nota").value("(No se factura)"))
                .andExpect(jsonPath("$.origenId").value(pedido.intValue()))
                .andExpect(jsonPath("$.origenNumero").value("0001-000120"))
                .andExpect(jsonPath("$.items[*].descripcion", contains("Guantes talla M", "Gasa estéril")))
                .andExpect(jsonPath("$.items[*].cantidad", contains(2, 4)))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(25.0))
                .andExpect(jsonPath("$.items[0].precioManual").value(true))
                .andExpect(jsonPath("$.items[1].precioUnitario").value(5.5))
                .andExpect(jsonPath("$.total").value(72.0));

        // El stock bajó solo con lo entregado y lo que falta está en "Por comprar"
        mockMvc.perform(get("/api/productos/{id}", guantes)).andExpect(jsonPath("$.stockDisponible").value(0));
        mockMvc.perform(get(URL + "/por-comprar"))
                .andExpect(jsonPath("$.productos[*].nombre", contains("Gasa estéril", "Guantes talla M")));

        // En la lista, el saldo se reconoce
        mockMvc.perform(get(URL).param("estado", "BORRADOR"))
                .andExpect(jsonPath("$.contenido[0].id").value(saldo.intValue()))
                .andExpect(jsonPath("$.contenido[0].origenNumero").value("0001-000120"));

        // La nota que recibe el cliente no menciona el saldo
        byte[] pdf = mockMvc.perform(get(URL + "/{id}/nota.pdf", pedido)).andReturn().getResponse()
                .getContentAsByteArray();
        try (PdfReader lector = new PdfReader(pdf)) {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertThat(texto).contains("Jeringa 5 ml", "185.00").doesNotContain("Gasa", "72.00", "257.00",
                    "(No se factura)");
        }

        // Llega la gasa y el saldo se completa con su propia nota
        entrada(guantes, "G2", 5);
        entrada(gasa, "GA1", 10);
        postJson(URL + "/" + saldo + "/despachar", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("0001-000121"))
                .andExpect(jsonPath("$.grupo.entregado").value(257.0))
                .andExpect(jsonPath("$.grupo.pendiente").value(0.0));
    }

    @Test
    void elSaldoConservaLaPrioridadDelPedidoOriginal() throws Exception {
        Long guantes = producto("Guantes talla M", "10");
        entrada(guantes, "G1", 4);
        Long primero = pedido(cliente("Farmacia Primera"), "NOTA");
        agregar(primero, guantes, 10);                // hay 4: le faltan 6
        Long segundo = pedido(cliente("Clínica Segunda"), "NOTA");
        agregar(segundo, guantes, 3);                 // no le queda nada

        Long saldo = numero(postJson(URL + "/" + primero + "/despachar", PARCIAL)
                .andExpect(jsonPath("$.items[0].cantidad").value(4)), "$.grupo.pedidos[1].id");

        // Llegan 5: van al saldo de la farmacia (que pidió primero), aunque el saldo es más nuevo
        entrada(guantes, "G2", 5);
        mockMvc.perform(get(URL + "/{id}", saldo))
                .andExpect(jsonPath("$.items[0].cantidad").value(6))
                .andExpect(jsonPath("$.items[0].disponible").value(5))
                .andExpect(jsonPath("$.items[0].faltante").value(1));
        mockMvc.perform(get(URL + "/{id}", segundo))
                .andExpect(jsonPath("$.items[0].disponible").value(0))
                .andExpect(jsonPath("$.items[0].faltante").value(3));
    }

    @Test
    void siNoHayNadaQueEntregarNoSeDespacha() throws Exception {
        Long gasa = producto("Gasa estéril", "5");
        Long pedido = pedido(cliente("Botica Demo"), "NOTA");
        agregar(pedido, gasa, 4).andExpect(jsonPath("$.puedeEntregarParcial").value(false));

        postJson(URL + "/" + pedido + "/despachar", PARCIAL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("No hay nada que entregar")));
        mockMvc.perform(get(URL + "/{id}", pedido)).andExpect(jsonPath("$.estado").value("BORRADOR"));
    }

    @Test
    void anularElSaldoDejaRegistradoLoQueNoSeEntrego() throws Exception {
        Long guantes = producto("Guantes talla M", "10");
        entrada(guantes, "G1", 2);
        Long pedido = pedido(cliente("Botica Demo"), "NOTA");
        agregar(pedido, guantes, 5);
        Long saldo = numero(postJson(URL + "/" + pedido + "/despachar", PARCIAL), "$.grupo.pedidos[1].id");

        postJson(URL + "/" + saldo + "/anular", "").andExpect(status().isOk());
        mockMvc.perform(get(URL + "/{id}", pedido))
                .andExpect(jsonPath("$.grupo.pedido").value(55.0))
                .andExpect(jsonPath("$.grupo.entregado").value(22.0))
                .andExpect(jsonPath("$.grupo.pendiente").value(0.0))
                .andExpect(jsonPath("$.grupo.anulado").value(33.0));
    }

    // ------------------------------------------------------------------ solo tabla

    @Test
    void soloTablaNoLlevaNombresNiNumeroYNoGastaLaNumeracion() throws Exception {
        putJson("/api/negocio", """
                {"nombre": "Distribuidora Demo", "direccion": "Av. Principal 123", "telefono": "999 888 777"}""")
                .andExpect(status().isOk());
        putJson(URL + "/numeracion", """
                {"siguiente": 50}""").andExpect(status().isOk());
        Long jeringas = producto("Jeringa 5 ml", "10");
        entrada(jeringas, "J1", 20);
        Long pedido = pedido(cliente("Botica Santa Rosa"), "SOLO_TABLA");
        agregar(pedido, jeringas, 3).andExpect(jsonPath("$.documento").value("SOLO_TABLA"));

        postJson(URL + "/" + pedido + "/despachar", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESPACHADO"))
                .andExpect(jsonPath("$.numero").value(nullValue()));
        mockMvc.perform(get(URL + "/numeracion")).andExpect(jsonPath("$.proximoNumero").value("0001-000050"));
        mockMvc.perform(get("/api/productos/{id}", jeringas)).andExpect(jsonPath("$.stockDisponible").value(17));
        mockMvc.perform(get("/api/movimientos").param("productoId", jeringas.toString()))
                .andExpect(jsonPath("$.contenido[0].nota").value("Pedido " + pedido + " (solo tabla)"));

        // Excel: la tabla empieza en la primera fila; no hay nombres
        byte[] xlsx = mockMvc.perform(get(URL + "/{id}/nota.xlsx", pedido))
                .andExpect(header().string("Content-Disposition", containsString("pedido-" + pedido + ".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(0).getCell(1).getStringCellValue()).isEqualTo("DESCRIPCIÓN");
            assertThat(hoja.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Jeringa 5 ml");
            assertThat(hoja.getRow(1).getCell(4).getStringCellValue()).isEqualTo("J1");
            assertThat(hoja.getRow(2).getCell(5).getStringCellValue()).isEqualTo("TOTAL S/");
            assertThat(hoja.getRow(2).getCell(7).getNumericCellValue()).isEqualTo(33.0); // en Precio + Total
            StringBuilder todo = new StringBuilder();
            hoja.forEach(fila -> fila.forEach(celda -> todo.append(celda).append(' ')));
            assertThat(todo.toString()).doesNotContain("Distribuidora", "Botica", "NOTA", "Fecha");
        }

        // PDF: igual, solo la tabla
        byte[] pdf = mockMvc.perform(get(URL + "/{id}/nota.pdf", pedido)).andReturn().getResponse()
                .getContentAsByteArray();
        try (PdfReader lector = new PdfReader(pdf)) {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertThat(texto).contains("DESCRIPCIÓN", "Jeringa 5 ml", "TOTAL S/", "33.00")
                    .doesNotContain("Distribuidora", "Botica", "NOTA DE PEDIDO", "Fecha", "(No se factura)");
        }
    }

    @Test
    void elDocumentoSeCambiaEnElBorradorYElSaldoLoHereda() throws Exception {
        Long guantes = producto("Guantes talla M", "10");
        entrada(guantes, "G1", 1);
        Long pedido = pedido(cliente("Botica Demo"), "NOTA");
        agregar(pedido, guantes, 3);
        putJson(URL + "/" + pedido, """
                {"documento": "SOLO_TABLA", "nota": "(No se factura)"}""")
                .andExpect(jsonPath("$.documento").value("SOLO_TABLA"));

        Long saldo = numero(postJson(URL + "/" + pedido + "/despachar", PARCIAL)
                .andExpect(jsonPath("$.numero").value(nullValue())), "$.grupo.pedidos[1].id");
        mockMvc.perform(get(URL + "/{id}", saldo))
                .andExpect(jsonPath("$.documento").value("SOLO_TABLA"))
                .andExpect(jsonPath("$.origenNumero").value(nullValue()));
        mockMvc.perform(get(URL).param("estado", "DESPACHADO"))
                .andExpect(jsonPath("$.contenido[0].documento").value("SOLO_TABLA"))
                .andExpect(jsonPath("$.contenido[0].nota").value("(No se factura)"));
    }

    // ------------------------------------------------------------------ ítem a mano → producto

    @Test
    void unItemAManoPasaASerElProductoRegistradoConSuCantidadYPrecio() throws Exception {
        Long pedido = pedido(cliente("Botica Demo"), "NOTA");
        Long itemConPrecio = numero(postJson(URL + "/" + pedido + "/items", """
                {"descripcion": "Termómetro digital", "cantidad": 2, "precio": 30}"""), "$.items[0].id");
        Long itemSinPrecio = numero(postJson(URL + "/" + pedido + "/items", """
                {"descripcion": "Tensiómetro", "cantidad": 1}"""), "$.items[1].id");
        mockMvc.perform(get(URL + "/por-comprar"))
                .andExpect(jsonPath("$.noRegistrados[0].itemId").value(itemConPrecio.intValue()));

        Long termometro = producto("Termómetro digital infrarrojo", "20");
        putJson(URL + "/" + pedido + "/items/" + itemConPrecio + "/producto", """
                {"productoId": %d}""".formatted(termometro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productoId").value(termometro.intValue()))
                .andExpect(jsonPath("$.items[0].descripcion").value("Termómetro digital infrarrojo"))
                .andExpect(jsonPath("$.items[0].presentacion").value("Caja x 100"))
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(30.0)) // se respeta el precio dado
                .andExpect(jsonPath("$.items[0].precioManual").value(true))
                .andExpect(jsonPath("$.items[0].disponibilidad").value("SIN_STOCK"));

        // Sin precio: se calcula con el costo + porcentaje
        Long tensiometro = producto("Tensiómetro de brazo", "50");
        putJson(URL + "/" + pedido + "/items/" + itemSinPrecio + "/producto", """
                {"productoId": %d}""".formatted(tensiometro))
                .andExpect(jsonPath("$.items[1].precioUnitario").value(55.0))
                .andExpect(jsonPath("$.items[1].precioManual").value(false));

        // Ya no es a mano, y un producto no se repite en el pedido
        putJson(URL + "/" + pedido + "/items/" + itemSinPrecio + "/producto", """
                {"productoId": %d}""".formatted(tensiometro)).andExpect(status().isConflict());
        Long otro = numero(postJson(URL + "/" + pedido + "/items", """
                {"descripcion": "Termómetro", "cantidad": 1}"""), "$.items[2].id");
        putJson(URL + "/" + pedido + "/items/" + otro + "/producto", """
                {"productoId": %d}""".formatted(termometro))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("ya está en el pedido")));
    }
}
