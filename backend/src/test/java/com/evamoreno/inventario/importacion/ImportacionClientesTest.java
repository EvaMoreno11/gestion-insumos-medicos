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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

/**
 * Importa archivos construidos a partir de la PLANTILLA REAL (docs/plantilla_clientes.xlsx),
 * así este test falla si la plantilla y el importador dejan de coincidir.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ImportacionClientesTest extends IntegracionTest {

    private static final Path PLANTILLA = Path.of("../docs/plantilla_clientes.xlsx");
    private static final String URL = "/api/importacion/clientes";

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM cliente");
    }

    /** Copia la plantilla y escribe las filas en la hoja Clientes (desde la fila 2 de Excel). */
    private byte[] excel(Object[]... filas) throws Exception {
        try (InputStream in = Files.newInputStream(PLANTILLA); XSSFWorkbook libro = new XSSFWorkbook(in)) {
            Sheet hoja = libro.getSheet("Clientes");
            for (int i = 0; i < filas.length; i++) {
                Row row = hoja.getRow(i + 1) == null ? hoja.createRow(i + 1) : hoja.getRow(i + 1);
                for (int c = 0; c < filas[i].length; c++) {
                    Object valor = filas[i][c];
                    Cell cell = row.getCell(c) == null ? row.createCell(c) : row.getCell(c);
                    switch (valor) {
                        case null -> cell.setBlank();
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
                .file(new MockMultipartFile("archivo", "clientes.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", archivo))
                .param("soloValidar", String.valueOf(soloValidar)));
    }

    //  Nombre | RUC o DNI | Local | Dirección de entrega | Contacto | Teléfono | Porcentaje | Observaciones
    private static final Object[] BOTICA_LOCAL_1 = { "Botica Demo S.A.C.", 20123456789L, "Local 1",
            "Av. Los Olivos 123", "Dra. Ana", 987654321, 15, null };
    private static final Object[] BOTICA_LOCAL_2 = { "BOTICA DEMO S.A.C.", null, "Local 2",
            "Jr. Las Flores 456", "Sr. Luis", "912 345 678", null, null };
    private static final Object[] CLINICA = { "Clínica Ejemplo", "20987654321", null, "Av. Central 789", null, null,
            "10%", "Un solo local" };
    private static final Object[] CONSULTORIO = { "Juan Pérez (consultorio)", "45678912", null, null, null, null,
            null, null };

    @Test
    void importaClientesAgrupandoLasFilasDelMismoClienteEnLocales() throws Exception {
        importar(excel(BOTICA_LOCAL_1, BOTICA_LOCAL_2, CLINICA, CONSULTORIO), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importado").value(true))
                .andExpect(jsonPath("$.errores", empty()))
                .andExpect(jsonPath("$.filasLeidas").value(4))
                .andExpect(jsonPath("$.clientesCreados").value(3))
                .andExpect(jsonPath("$.localesCreados").value(4));

        String pagina = mockMvc.perform(get("/api/clientes").param("buscar", "botica demo"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].rucDni").value("20123456789")) // escrito como número en Excel
                .andExpect(jsonPath("$.contenido[0].categoria").value("B"))
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(pagina, "$.contenido[0].id")).longValue();

        mockMvc.perform(get("/api/clientes/{id}", id))
                .andExpect(jsonPath("$.locales[*].nombre", contains("Local 1", "Local 2")))
                .andExpect(jsonPath("$.locales[0].telefono").value("987654321"))
                .andExpect(jsonPath("$.locales[1].contacto").value("Sr. Luis"));

        mockMvc.perform(get("/api/clientes").param("buscar", "clinica"))
                .andExpect(jsonPath("$.contenido[0].porcentaje").value(10.0))
                .andExpect(jsonPath("$.contenido[0].categoria").value("A"));
    }

    @Test
    void soloValidarNoGuardaNada() throws Exception {
        importar(excel(BOTICA_LOCAL_1, CLINICA), true)
                .andExpect(jsonPath("$.importado").value(false))
                .andExpect(jsonPath("$.clientesCreados").value(2))
                .andExpect(jsonPath("$.errores", empty()));

        mockMvc.perform(get("/api/clientes")).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void siUnaFilaTieneErroresNoSeGuardaNingunCliente() throws Exception {
        byte[] archivo = excel(
                CLINICA,                                                                       // fila 2: bien
                new Object[] { null, null, "Local 1", "Sin nombre", null, null, null, null },  // fila 3: sin nombre
                new Object[] { "Botica Uno", "123", null, null, null, null, null, null },       // fila 4: RUC inválido
                new Object[] { "Botica Dos", null, null, null, null, null, "quince", null },    // fila 5: porcentaje
                new Object[] { "Botica Tres", null, "Local 1", null, null, null, 10, null },    // fila 6
                new Object[] { "Botica Tres", null, "Local 2", null, null, null, 20, null },    // fila 7: otro porcentaje
                new Object[] { "Botica Cuatro", null, null, "Dirección A", null, null, null, null },   // fila 8
                new Object[] { "Botica Cuatro", null, null, "Dirección B", null, null, null, null },   // fila 9: sin local
                new Object[] { "Botica Cinco", "20987654321", null, null, null, null, null, null });   // fila 10: RUC de la clínica

        importar(archivo, false)
                .andExpect(jsonPath("$.importado").value(false))
                .andExpect(jsonPath("$.errores[*].fila", contains(3, 4, 5, 7, 8, 10)))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Nombre o razón social: es obligatorio"))
                .andExpect(jsonPath("$.errores[1].mensaje", containsString("8 dígitos (DNI) u 11 dígitos (RUC)")))
                .andExpect(jsonPath("$.errores[2].mensaje", containsString("no es un número")))
                .andExpect(jsonPath("$.errores[3].mensaje", containsString("es uno solo por cliente")))
                .andExpect(jsonPath("$.errores[4].mensaje", containsString("indica el nombre de cada local")))
                .andExpect(jsonPath("$.errores[5].mensaje", containsString("ya pertenece al cliente")));

        mockMvc.perform(get("/api/clientes")).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void losClientesQueYaExistenNoSeModifican() throws Exception {
        importar(excel(CLINICA), false).andExpect(jsonPath("$.importado").value(true));

        importar(excel(CLINICA, CONSULTORIO), false)
                .andExpect(jsonPath("$.importado").value(true))
                .andExpect(jsonPath("$.clientesCreados").value(1))
                .andExpect(jsonPath("$.clientesExistentes").value(1))
                .andExpect(jsonPath("$.advertencias", hasSize(1)))
                .andExpect(jsonPath("$.advertencias[0].mensaje", containsString("ya está registrado")));
    }

    @Test
    void unaPlantillaVaciaNoImportaNada() throws Exception {
        importar(Files.readAllBytes(PLANTILLA), false)
                .andExpect(jsonPath("$.importado").value(false))
                .andExpect(jsonPath("$.errores[0].mensaje", containsString("no tiene filas con datos")));
    }
}
