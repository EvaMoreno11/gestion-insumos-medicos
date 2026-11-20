package com.evamoreno.inventario.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;

/** Inventario completo y catálogo. "Hoy" es el 15/06/2026 (reloj fijo de los tests). */
class ExportacionControllerTest extends IntegracionTest {

    private static final String URL = "/api/exportar";
    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long producto(String categoria, String nombre, String marca, String costo, int minimo) throws Exception {
        return crear("/api/productos", """
                {"nombre": "%s", "marca": "%s", "categoriaId": %d, "presentacion": "Caja x 100",
                 "unidadMedida": "Caja", "costo": %s, "stockMinimo": %d}"""
                .formatted(nombre, marca, idCategoria(categoria), costo, minimo));
    }

    private void entrada(Long productoId, String lote, LocalDate vence, int cantidad) throws Exception {
        postJson("/api/movimientos/entrada", """
                {"productoId": %d, "numeroLote": "%s", "fechaVencimiento": "%s", "cantidad": %d, "costo": 10}"""
                .formatted(productoId, lote, vence, cantidad)).andExpect(status().isCreated());
    }

    private byte[] descargar(String ruta) throws Exception {
        return mockMvc.perform(get(URL + ruta)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private static String pdf(byte[] contenido) throws Exception {
        try (PdfReader lector = new PdfReader(contenido)) {
            return new PdfTextExtractor(lector).getTextFromPage(1);
        }
    }

    /**
     * Jeringa: 3 vigentes + 2 vencidas, costo 10, mínimo 5. Guantes: sin costo ni stock, mínimo 2.
     * Lima: sin mínimo (no va en el catálogo). Producto viejo: desactivado (no va en ninguno).
     */
    @BeforeEach
    void datos() throws Exception {
        mockMvc.perform(put("/api/negocio").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre": "Distribuidora Demo", "direccion": "Av. Principal 123", "telefono": "999 888 777"}"""))
                .andExpect(status().isOk());
        Long jeringa = producto("Jeringas", "Jeringa 5 ml", "Marca A", "10", 5);
        entrada(jeringa, "L1", HOY.plusMonths(12), 3);
        entrada(jeringa, "VENC", HOY.minusMonths(1), 2);
        producto("Otros", "Guantes M", "Marca B", "null", 2);
        producto("Otros", "Lima de uñas", "Genérico", "1.5", 0);
        Long viejo = producto("Otros", "Producto viejo", "Marca C", "3", 3);
        mockMvc.perform(patch("/api/productos/{id}/desactivar", viejo)).andExpect(status().isOk());
    }

    @Test
    void inventarioCompletoConTodosLosProductosActivosYSusLotes() throws Exception {
        byte[] xlsx = mockMvc.perform(get(URL + "/inventario.xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inventario-2026-06-15.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheet("Inventario");
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Inventario al 15/06/2026");
            assertThat(hoja.getRow(3).getCell(2).getStringCellValue()).isEqualTo("NOMBRE DEL PRODUCTO");

            Row jeringa = hoja.getRow(4); // por categoría: Jeringas antes que Otros
            assertThat(jeringa.getCell(1).getStringCellValue()).isEqualTo("Jeringas");
            assertThat(jeringa.getCell(2).getStringCellValue()).isEqualTo("Jeringa 5 ml");
            assertThat(jeringa.getCell(6).getNumericCellValue()).isEqualTo(3);  // las vencidas no cuentan
            assertThat(jeringa.getCell(7).getNumericCellValue()).isEqualTo(5);
            assertThat(jeringa.getCell(8).getNumericCellValue()).isEqualTo(10);
            assertThat(jeringa.getCell(9).getNumericCellValue()).isEqualTo(30);

            assertThat(hoja.getRow(5).getCell(2).getStringCellValue()).isEqualTo("Guantes M");
            assertThat(hoja.getRow(5).getCell(6).getNumericCellValue()).isZero();  // sin stock, pero está
            assertThat(hoja.getRow(6).getCell(2).getStringCellValue()).isEqualTo("Lima de uñas");
            assertThat(hoja.getRow(7)).isNull();                                 // el desactivado no

            Sheet lotes = libro.getSheet("Lotes");
            assertThat(lotes.getRow(3).getCell(4).getStringCellValue()).isEqualTo("VENC");
            assertThat(lotes.getRow(3).getCell(8).getStringCellValue()).isEqualTo("Vencido");
            assertThat(lotes.getRow(4).getCell(4).getStringCellValue()).isEqualTo("L1");
            assertThat(lotes.getRow(4).getCell(5).getStringCellValue()).isEqualTo("06/2027");
            assertThat(lotes.getRow(4).getCell(6).getNumericCellValue()).isEqualTo(3);
            assertThat(lotes.getRow(4).getCell(8).getStringCellValue()).isEqualTo("Vigente");
        }
    }

    @Test
    void catalogoSinPreciosSoloConLosProductosQueTienenMinimo() throws Exception {
        String texto = pdf(descargar("/catalogo.pdf"));
        assertThat(texto).contains("Distribuidora Demo", "CATÁLOGO DE PRODUCTOS", "Actualizado al 15/06/2026",
                "JERINGAS", "Jeringa 5 ml", "OTROS", "Guantes M");
        assertThat(texto).doesNotContain("Lima de uñas", "Producto viejo", "S/", "PRECIO", "Consultar");

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(descargar("/catalogo.xlsx")))) {
            Sheet hoja = libro.getSheet("Catálogo");
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Distribuidora Demo");
            assertThat(hoja.getRow(7).getCell(0).getStringCellValue()).isEqualTo("JERINGAS");
            assertThat(hoja.getRow(8).getCell(0).getStringCellValue()).isEqualTo("Jeringa 5 ml");
            assertThat(hoja.getRow(8).getLastCellNum()).isEqualTo((short) 3); // sin columna de precio
        }
    }

    @Test
    void listaDePreciosParaUnClienteConSuPorcentaje() throws Exception {
        Long botica = crear("/api/clientes", """
                {"nombre": "Botica Santa Rosa", "porcentaje": 15}""");

        byte[] contenido = mockMvc.perform(get(URL + "/catalogo.pdf").param("clienteId", botica.toString()))
                .andExpect(header().string("Content-Disposition", containsString("lista-de-precios-2026-06-15.pdf")))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andReturn().getResponse().getContentAsByteArray();
        String texto = pdf(contenido);
        assertThat(texto).contains("LISTA DE PRECIOS", "Cliente: Botica Santa Rosa", "Precios incluyen IGV",
                "S/ 11.50", "Consultar"); // 10 + 15 %; los guantes no tienen costo
        assertThat(texto).doesNotContain("15 %", "10.00");               // ni porcentaje ni costo

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(
                mockMvc.perform(get(URL + "/catalogo.xlsx").param("clienteId", botica.toString()))
                        .andReturn().getResponse().getContentAsByteArray()))) {
            Sheet hoja = libro.getSheet("Lista de precios");
            assertThat(hoja.getRow(8).getCell(3).getNumericCellValue()).isEqualTo(11.5);
            assertThat(hoja.getRow(10).getCell(3).getStringCellValue()).isEqualTo("Consultar");
        }

        Long sinPorcentaje = crear("/api/clientes", """
                {"nombre": "Clínica Nueva"}""");
        mockMvc.perform(get(URL + "/catalogo.pdf").param("clienteId", sinPorcentaje.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("aún no tiene porcentaje")));
        mockMvc.perform(get(URL + "/catalogo.pdf").param("clienteId", "999999")).andExpect(status().isNotFound());
    }
}
