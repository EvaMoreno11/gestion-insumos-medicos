package com.evamoreno.inventario.importacion;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.IntegracionTest;

/**
 * Importa archivos construidos a partir de la PLANTILLA REAL (docs/plantilla_inventario.xlsx),
 * así este test falla si la plantilla y el importador dejan de coincidir.
 *
 * <p>No usa la transacción del test (la importación maneja la suya y hace rollback cuando
 * corresponde); por eso limpia la base de datos al terminar cada test.</p>
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ImportacionControllerTest extends IntegracionTest {

    private static final Path PLANTILLA = Path.of("../docs/plantilla_inventario.xlsx");
    private static final String URL = "/api/importacion/inventario";

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private LocalDate hoy;
    private long ultimaCategoriaInicial;

    @BeforeEach
    void preparar() {
        hoy = LocalDate.now(clock);
        ultimaCategoriaInicial = jdbc.queryForObject("SELECT max(id) FROM categoria", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM movimiento");
        jdbc.update("DELETE FROM lote");
        jdbc.update("DELETE FROM producto_codigo_barras");
        jdbc.update("DELETE FROM producto");
        jdbc.update("DELETE FROM categoria WHERE id > ?", ultimaCategoriaInicial);
    }

    /** Copia la plantilla y escribe las filas en la hoja Inventario (desde la fila 2 de Excel). */
    private byte[] excel(Object[]... filas) throws Exception {
        try (InputStream in = Files.newInputStream(PLANTILLA); XSSFWorkbook libro = new XSSFWorkbook(in)) {
            Sheet hoja = libro.getSheet("Inventario");
            for (int i = 0; i < filas.length; i++) {
                Row row = hoja.getRow(i + 1) == null ? hoja.createRow(i + 1) : hoja.getRow(i + 1);
                for (int c = 0; c < filas[i].length; c++) {
                    Object valor = filas[i][c];
                    Cell cell = row.getCell(c) == null ? row.createCell(c) : row.getCell(c);
                    switch (valor) {
                        case null -> cell.setBlank();
                        case LocalDate fecha -> cell.setCellValue(fecha);
                        case Number numero -> cell.setCellValue(numero.doubleValue());
                        default -> cell.setCellValue(valor.toString());
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            libro.write(out);
            return out.toByteArray();
        }
    }

    private ResultActions importar(byte[] archivo, boolean soloValidar) throws Exception {
        return mockMvc.perform(multipart(URL)
                .file(new MockMultipartFile("archivo", "inventario.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", archivo))
                .param("soloValidar", String.valueOf(soloValidar)));
    }

    //               Código Categoría   Nombre del producto   Marca  Presentación  Unidad  Mín  Cód. barras  Lote  F.V.  Cantidad
    private Object[] jeringaLote1() {
        return new Object[] { null, "Jeringas", "Jeringa descartable 5 ml", "Marca A", "Caja x 100", "Caja", 5,
                "2000000000039", "L1", hoy.plusDays(100), 12 };
    }

    private Object[] jeringaLote2() {
        // Mismo producto, otro lote. Código de barras escrito como NÚMERO y fecha solo con mes/año.
        return new Object[] { null, "Jeringas", "Jeringa descartable 5 ml", "Marca A", "Caja x 100", "Caja", 5,
                2000000000039L, "L2", "01/2030", 8 };
    }

    @Test
    void importaProductosLotesYCategoriasNuevas() throws Exception {
        byte[] archivo = excel(
                jeringaLote2(), // crea el producto con el código de barras escrito como número
                jeringaLote1(),
                new Object[] { null, "Material de curación", "Bajalenguas de madera", "Marca E", "Paquete x 100",
                        "Paquete", null, null, null, null, 30 },
                new Object[] { null, "gasas y apositos", "Gasa estéril 10 x 10 cm", "Marca D", "Paquete x 10",
                        "Paquete", 15, null, null, null, 0 });

        importar(archivo, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importado").value(true))
                .andExpect(jsonPath("$.errores", empty()))
                .andExpect(jsonPath("$.filasLeidas").value(4))
                .andExpect(jsonPath("$.productosCreados").value(3))
                .andExpect(jsonPath("$.categoriasCreadas").value(1))  // "gasas y apositos" = "Gasas y apósitos"
                .andExpect(jsonPath("$.entradasRegistradas").value(3)) // la gasa tiene cantidad 0
                .andExpect(jsonPath("$.unidadesIngresadas").value(50));

        mockMvc.perform(get("/api/productos/codigo-barras/{codigo}", "2000000000039"))
                .andExpect(jsonPath("$.stockDisponible").value(20))
                .andExpect(jsonPath("$.lotes[*].numeroLote", contains("L1", "L2")))
                .andExpect(jsonPath("$.lotes[0].fechaVencimiento").value("2026-09-30"))  // 23/09 -> fin de mes
                .andExpect(jsonPath("$.lotes[1].fechaVencimiento").value("2030-01-31")); // 01/2030 -> fin de mes

        mockMvc.perform(get("/api/categorias")).andExpect(jsonPath("$", hasSize(12)));
    }

    /** Agrega el costo (columna M) a una fila de la plantilla. */
    private static Object[] conCosto(Object[] fila, Object costo) {
        Object[] completa = java.util.Arrays.copyOf(fila, 13);
        completa[12] = costo;
        return completa;
    }

    @Test
    void importaElCostoYLaAppCalculaLosPrecios() throws Exception {
        // El costo es del producto: basta con que venga en una de sus filas
        importar(excel(conCosto(jeringaLote1(), 8.5), jeringaLote2()), false)
                .andExpect(jsonPath("$.importado").value(true))
                .andExpect(jsonPath("$.errores", empty()));

        mockMvc.perform(get("/api/productos/codigo-barras/{codigo}", "2000000000039"))
                .andExpect(jsonPath("$.costo").value(8.5))
                .andExpect(jsonPath("$.precios[*].precio", contains(9.35, 9.78, 10.2)));

        // Volver a importar con otro costo lo actualiza
        importar(excel(conCosto(jeringaLote1(), "9,00")), false).andExpect(jsonPath("$.importado").value(true));
        mockMvc.perform(get("/api/productos/codigo-barras/{codigo}", "2000000000039"))
                .andExpect(jsonPath("$.costo").value(9.0));
    }

    @Test
    void rechazaDosCostosDistintosParaElMismoProducto() throws Exception {
        importar(excel(conCosto(jeringaLote1(), 8.5), conCosto(jeringaLote2(), 9)), false)
                .andExpect(jsonPath("$.importado").value(false))
                .andExpect(jsonPath("$.errores[*].fila", contains(3)))
                .andExpect(jsonPath("$.errores[0].mensaje", containsString("tiene otro costo (8.5)")));

        importar(excel(conCosto(jeringaLote1(), "caro")), false)
                .andExpect(jsonPath("$.errores[0].mensaje", containsString("Costo:")));
    }

    @Test
    void soloValidarRevisaTodoPeroNoGuardaNada() throws Exception {
        importar(excel(jeringaLote1(), jeringaLote2()), true)
                .andExpect(jsonPath("$.importado").value(false))
                .andExpect(jsonPath("$.soloValidar").value(true))
                .andExpect(jsonPath("$.productosCreados").value(1))
                .andExpect(jsonPath("$.entradasRegistradas").value(2))
                .andExpect(jsonPath("$.errores", empty()));

        mockMvc.perform(get("/api/productos").param("buscar", "jeringa"))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void siUnaFilaTieneErroresNoSeGuardaNingunaFila() throws Exception {
        byte[] archivo = excel(
                jeringaLote1(),                                                                  // fila 2: bien
                new Object[] { null, "Guantes", "Guantes de látex talla M", null, "Caja x 100", "Caja", null,
                        null, "G1", null, 10 },                                                  // fila 3: sin marca
                new Object[] { null, "Guantes", "Guantes de látex talla S", "Marca B", "Caja x 100", "Caja", null,
                        null, "G2", "31/02/2027", 10 },                                          // fila 4: fecha inválida
                new Object[] { null, "Guantes", "Guantes de látex talla L", "Marca B", "Caja x 100", "Caja", null,
                        null, "G3", null, "diez" },                                              // fila 5: cantidad
                new Object[] { null, "Jeringas", "Jeringa descartable 5 ml", "Marca A", "Caja x 100", "Caja", 5,
                        null, "L1", hoy.plusDays(999), 1 });                                     // fila 6: lote con otra F.V.

        importar(archivo, false)
                .andExpect(jsonPath("$.importado").value(false))
                .andExpect(jsonPath("$.errores[*].fila", contains(3, 4, 5, 6)))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Marca: es obligatorio"))
                .andExpect(jsonPath("$.errores[1].mensaje", containsString("no es una fecha válida")))
                .andExpect(jsonPath("$.errores[2].mensaje", containsString("no es un número entero")))
                .andExpect(jsonPath("$.errores[3].mensaje", containsString("ya está registrado con vencimiento")));

        mockMvc.perform(get("/api/productos").param("buscar", "jeringa"))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void advierteSiElProductoYaTeniaStock() throws Exception {
        importar(excel(jeringaLote1()), false).andExpect(jsonPath("$.importado").value(true));

        importar(excel(jeringaLote1()), false)
                .andExpect(jsonPath("$.importado").value(true))
                .andExpect(jsonPath("$.productosExistentes").value(1))
                .andExpect(jsonPath("$.advertencias", hasSize(1)))
                .andExpect(jsonPath("$.advertencias[0].mensaje", containsString("ya tenía 12 unidades")));
    }

    @Test
    void unaPlantillaVaciaNoImportaNada() throws Exception {
        importar(Files.readAllBytes(PLANTILLA), false)
                .andExpect(jsonPath("$.importado").value(false))
                .andExpect(jsonPath("$.errores[0].mensaje", containsString("no tiene filas con datos")));
    }

    @Test
    void rechazaArchivosQueNoSonExcel() throws Exception {
        mockMvc.perform(multipart(URL).file(new MockMultipartFile("archivo", "inventario.csv", "text/csv",
                "a,b,c".getBytes())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart(URL).file(new MockMultipartFile("archivo", "falso.xlsx",
                "application/octet-stream", "no soy un excel".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("No se pudo leer el archivo")));
    }

    @Test
    void rechazaUnaHojaSinLasColumnasObligatorias() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Row encabezado = libro.createSheet("Inventario").createRow(0);
            encabezado.createCell(0).setCellValue("Nombre del producto");
            encabezado.createCell(1).setCellValue("Cantidad");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            libro.write(out);

            importar(out.toByteArray(), true)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("Faltan columnas")))
                    .andExpect(jsonPath("$.detail", containsString("Marca")));
        }
    }
}
