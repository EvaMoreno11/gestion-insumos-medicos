package com.evamoreno.inventario.cliente;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

class ClienteControllerTest extends IntegracionTest {

    private static final String URL = "/api/clientes";

    private ResultActions enviar(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions actualizar(Long id, String json) throws Exception {
        return mockMvc.perform(put(URL + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ------------------------------------------------------------------ crear

    @Test
    void creaUnClienteConVariosLocalesYMuestraSuCategoria() throws Exception {
        enviar("""
                {"nombre": "  Botica   Demo S.A.C. ", "rucDni": "20123456789", "porcentaje": 15,
                 "locales": [
                   {"nombre": "Local 1", "direccion": "Av. Los Olivos 123", "contacto": "Dra. Ana", "telefono": "987654321"},
                   {"nombre": "Local 2", "direccion": "Jr. Las Flores 456"}
                 ]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Botica Demo S.A.C."))
                .andExpect(jsonPath("$.porcentaje").value(15))
                .andExpect(jsonPath("$.categoria").value("B"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.locales[*].nombre", contains("Local 1", "Local 2")))
                .andExpect(jsonPath("$.locales[0].contacto").value("Dra. Ana"));
    }

    @Test
    void sinLocalesSeRegistraUnUnicoPuntoDeEntregaYSinPorcentajeNoHayCategoria() throws Exception {
        enviar("""
                {"nombre": "Consultorio Dr. Pérez"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.porcentaje").value(nullValue()))
                .andExpect(jsonPath("$.categoria").value(nullValue()))
                .andExpect(jsonPath("$.locales", hasSize(1)))
                .andExpect(jsonPath("$.locales[0].nombre").value(nullValue()));
    }

    @Test
    void unPorcentajeQueNoEsDeNingunaCategoriaSeGuardaSinLetra() throws Exception {
        enviar("""
                {"nombre": "Clínica Ejemplo", "porcentaje": 12.5}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.porcentaje").value(12.5))
                .andExpect(jsonPath("$.categoria").value(nullValue()));
    }

    @Test
    void noSeRepiteElClienteNiElRuc() throws Exception {
        crear(URL, """
                {"nombre": "Botica Demo", "rucDni": "20123456789"}""");

        enviar("""
                {"nombre": "BOTICA  DEMO"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe el cliente \"Botica Demo\""));

        enviar("""
                {"nombre": "Otra Botica", "rucDni": "20123456789"}""")
                .andExpect(status().isConflict());
    }

    @Test
    void validaLosDatos() throws Exception {
        enviar("""
                {"nombre": " ", "rucDni": "123", "porcentaje": -5}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errores.rucDni").value("Debe tener 8 dígitos (DNI) u 11 dígitos (RUC)"))
                .andExpect(jsonPath("$.errores.porcentaje").value("Debe ser 0 o más"));
    }

    @Test
    void variosLocalesNecesitanNombresDistintos() throws Exception {
        enviar("""
                {"nombre": "Botica Demo", "locales": [{"direccion": "A"}, {"direccion": "B"}]}""")
                .andExpect(status().isConflict());
        enviar("""
                {"nombre": "Botica Demo", "locales": [{"nombre": "Local 1"}, {"nombre": "LOCAL 1"}]}""")
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ buscar

    @Test
    void buscaPorNombreORucSinImportarTildes() throws Exception {
        crear(URL, """
                {"nombre": "Clínica San José", "rucDni": "20555555551"}""");
        crear(URL, """
                {"nombre": "Botica San Martín", "rucDni": "20666666662"}""");
        Long inactivo = crear(URL, """
                {"nombre": "Botica Cerrada"}""");
        mockMvc.perform(patch(URL + "/{id}/desactivar", inactivo)).andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(get(URL).param("buscar", "clinica jose"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Clínica San José"));
        mockMvc.perform(get(URL).param("buscar", "2066"))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Botica San Martín")));
        mockMvc.perform(get(URL).param("buscar", "botica"))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(get(URL).param("buscar", "botica").param("incluirInactivos", "true"))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void buscaPorElNombreDelLocalOElContactoYDiceDondeLoEncontro() throws Exception {
        crear(URL, """
                {"nombre": "Juan Pérez Quispe", "rucDni": "10456789012",
                 "locales": [
                   {"nombre": "Botica Santa Rosa", "contacto": "Sra. Marta"},
                   {"nombre": "Botica El Niño", "contacto": "Dr. Ramírez"}
                 ]}""");
        crear(URL, """
                {"nombre": "Clínica Los Andes", "locales": [{"contacto": "Dra. Rosa Díaz"}]}""");

        // Por el nombre de la botica: aparece la razón social con su RUC y el local que coincidió
        mockMvc.perform(get(URL).param("buscar", "santa rosa"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Juan Pérez Quispe"))
                .andExpect(jsonPath("$.contenido[0].rucDni").value("10456789012"))
                .andExpect(jsonPath("$.contenido[0].localesEncontrados", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].localesEncontrados[0].nombre").value("Botica Santa Rosa"))
                .andExpect(jsonPath("$.contenido[0].localesEncontrados[0].contacto").value("Sra. Marta"));

        // Por el contacto, sin tildes y con el inicio de la palabra
        mockMvc.perform(get(URL).param("buscar", "rami"))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Juan Pérez Quispe")))
                .andExpect(jsonPath("$.contenido[0].localesEncontrados[0].nombre").value("Botica El Niño"));
        mockMvc.perform(get(URL).param("buscar", "nino"))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Juan Pérez Quispe")));

        // "rosa" está en un local de cada cliente
        mockMvc.perform(get(URL).param("buscar", "rosa"))
                .andExpect(jsonPath("$.contenido[*].nombre", contains("Clínica Los Andes", "Juan Pérez Quispe")))
                .andExpect(jsonPath("$.contenido[0].localesEncontrados[0].contacto").value("Dra. Rosa Díaz"));

        // Las palabras se pueden combinar entre la razón social y los locales
        mockMvc.perform(get(URL).param("buscar", "perez ramirez"))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(get(URL).param("buscar", "andes ramirez"))
                .andExpect(jsonPath("$.totalElementos").value(0));

        // Si coincide solo la razón social, no se listan locales
        mockMvc.perform(get(URL).param("buscar", "quispe"))
                .andExpect(jsonPath("$.contenido[0].localesEncontrados", hasSize(0)));
    }

    @Test
    void alCambiarElLocalSeActualizaLaBusqueda() throws Exception {
        String creado = enviar("""
                {"nombre": "Distribuidora Norte", "locales": [{"nombre": "Botica Antigua"}]}""")
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(creado, "$.id")).longValue();
        Long local = ((Number) JsonPath.read(creado, "$.locales[0].id")).longValue();

        actualizar(id, """
                {"nombre": "Distribuidora Norte", "locales": [{"id": %d, "nombre": "Botica Nueva Vida"}]}"""
                .formatted(local))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL).param("buscar", "antigua"))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(get(URL).param("buscar", "nueva vida"))
                .andExpect(jsonPath("$.contenido[0].localesEncontrados[0].nombre").value("Botica Nueva Vida"));
    }

    // ------------------------------------------------------------------ actualizar

    @Test
    void actualizaElPorcentajeYSincronizaLosLocales() throws Exception {
        String creado = enviar("""
                {"nombre": "Botica Demo", "porcentaje": 10,
                 "locales": [{"nombre": "Local 1", "direccion": "Dirección 1"}, {"nombre": "Local 2"}]}""")
                .andReturn().getResponse().getContentAsString();
        Long id = ((Number) JsonPath.read(creado, "$.id")).longValue();
        Long local1 = ((Number) JsonPath.read(creado, "$.locales[0].id")).longValue();

        // Local 1 se modifica (va con id), Local 2 se elimina (no se envía) y Local 3 se agrega (sin id)
        actualizar(id, """
                {"nombre": "Botica Demo", "porcentaje": 20,
                 "locales": [{"id": %d, "nombre": "Local 1", "direccion": "Dirección nueva"}, {"nombre": "Local 3"}]}"""
                .formatted(local1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("C"))
                .andExpect(jsonPath("$.locales[*].nombre", contains("Local 1", "Local 3")))
                .andExpect(jsonPath("$.locales[0].id").value(local1.intValue()))
                .andExpect(jsonPath("$.locales[0].direccion").value("Dirección nueva"));

        // Sin lista de locales, se conservan
        actualizar(id, """
                {"nombre": "Botica Demo Renombrada", "porcentaje": 20}""")
                .andExpect(jsonPath("$.nombre").value("Botica Demo Renombrada"))
                .andExpect(jsonPath("$.locales", hasSize(2)));

        mockMvc.perform(get(URL).param("buscar", "renombrada"))
                .andExpect(jsonPath("$.contenido[0].categoria").value("C"));
    }

    @Test
    void responde404SiElClienteOElLocalNoExisten() throws Exception {
        mockMvc.perform(get(URL + "/{id}", 999999)).andExpect(status().isNotFound());

        Long id = crear(URL, """
                {"nombre": "Botica Demo"}""");
        actualizar(id, """
                {"nombre": "Botica Demo", "locales": [{"id": 999999, "nombre": "Local X"}]}""")
                .andExpect(status().isNotFound());
    }
}
