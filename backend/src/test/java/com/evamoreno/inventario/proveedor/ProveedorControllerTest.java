package com.evamoreno.inventario.proveedor;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;
import com.jayway.jsonpath.JsonPath;

/** Deudas con proveedores y sus pagos. "Hoy" es el 15/06/2026 (reloj fijo de los tests). */
class ProveedorControllerTest extends IntegracionTest {

    private static final String URL = "/api/proveedores";

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long deuda(String proveedor, String fecha, String monto) throws Exception {
        return crear(URL + "/deudas", """
                {"proveedor": "%s", "fecha": "%s", "monto": %s, "nota": "Factura de prueba"}"""
                .formatted(proveedor, fecha, monto));
    }

    private ResultActions pagar(Long deuda, String monto) throws Exception {
        return postJson(URL + "/deudas/" + deuda + "/pagos", """
                {"monto": %s, "medio": "TRANSFERENCIA", "fecha": "2026-06-10"}""".formatted(monto));
    }

    @Test
    void anotarUnaDeudaCreaElProveedorYNoLoRepiteEscritoDistinto() throws Exception {
        postJson(URL + "/deudas", """
                {"proveedor": "  Distribuidora  Pérez ", "monto": 1500, "nota": "Factura F001-234"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proveedor").value("Distribuidora Pérez"))
                .andExpect(jsonPath("$.fecha").value("2026-06-15")) // sin fecha: hoy
                .andExpect(jsonPath("$.monto").value(1500.0))
                .andExpect(jsonPath("$.pagado").value(0.0))
                .andExpect(jsonPath("$.saldo").value(1500.0))
                .andExpect(jsonPath("$.nota").value("Factura F001-234"));
        deuda("DISTRIBUIDORA PEREZ", "2026-06-01", "200");
        deuda("Laboratorio Andino", "2026-06-02", "300");

        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$[*].nombre", contains("Distribuidora Pérez", "Laboratorio Andino")));
        mockMvc.perform(get(URL + "/resumen"))
                .andExpect(jsonPath("$.porPagar").value(2000.0))
                .andExpect(jsonPath("$.deudasPorPagar").value(3));
    }

    @Test
    void sePagaEnPartesYAlTerminarPasaAPagadas() throws Exception {
        Long antigua = deuda("Distribuidora Pérez", "2026-05-20", "1500");
        Long nueva = deuda("Laboratorio Andino", "2026-06-12", "300");

        // Por pagar: primero la más antigua
        mockMvc.perform(get(URL + "/deudas"))
                .andExpect(jsonPath("$.contenido[*].id", contains(antigua.intValue(), nueva.intValue())));

        pagar(antigua, "500")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagado").value(500.0))
                .andExpect(jsonPath("$.saldo").value(1000.0))
                .andExpect(jsonPath("$.pagadaEl").doesNotExist())
                .andExpect(jsonPath("$.pagos[0].medio").value("TRANSFERENCIA"));
        pagar(antigua, "1000.01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("El pago (S/ 1,000.01) es mayor que lo que falta pagar (S/ 1,000.00)"));
        postJson(URL + "/deudas/" + antigua + "/pagos", """
                {"monto": 1000, "medio": "EFECTIVO"}""") // "Pagó todo": lo que falta, hoy
                .andExpect(jsonPath("$.saldo").value(0.0))
                .andExpect(jsonPath("$.pagadaEl").value("2026-06-15"));
        pagar(antigua, "1").andExpect(status().isConflict());

        mockMvc.perform(get(URL + "/deudas"))
                .andExpect(jsonPath("$.contenido[*].id", contains(nueva.intValue())));
        mockMvc.perform(get(URL + "/deudas").param("pagadas", "true"))
                .andExpect(jsonPath("$.contenido[*].id", contains(antigua.intValue())))
                .andExpect(jsonPath("$.contenido[0].pagos.length()").value(2));
        mockMvc.perform(get(URL + "/resumen"))
                .andExpect(jsonPath("$.porPagar").value(300.0))
                .andExpect(jsonPath("$.deudasPorPagar").value(1));
    }

    @Test
    void borrarUnPagoMalAnotadoLaVuelveAPorPagar() throws Exception {
        Long id = deuda("Distribuidora Pérez", "2026-06-01", "100");
        String json = pagar(id, "100").andReturn().getResponse().getContentAsString();
        Long pago = ((Number) JsonPath.read(json, "$.pagos[0].id")).longValue();

        mockMvc.perform(delete(URL + "/deudas/{id}/pagos/{pago}", id, pago))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(100.0))
                .andExpect(jsonPath("$.pagos.length()").value(0));
        mockMvc.perform(delete(URL + "/deudas/{id}/pagos/{pago}", id, 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void corregirYBorrarUnaDeuda() throws Exception {
        Long id = deuda("Distribuidora Pérez", "2026-06-01", "1500");
        pagar(id, "600");

        mockMvc.perform(put(URL + "/deudas/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"proveedor": "Laboratorio Andino", "fecha": "2026-06-03", "monto": 1200, "nota": ""}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor").value("Laboratorio Andino"))
                .andExpect(jsonPath("$.fecha").value("2026-06-03"))
                .andExpect(jsonPath("$.saldo").value(600.0))
                .andExpect(jsonPath("$.nota").doesNotExist());
        mockMvc.perform(put(URL + "/deudas/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"proveedor": "Laboratorio Andino", "monto": 500}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya se le pagó S/ 600.00: el monto no puede ser menor"));

        // Con pagos no se borra; sin pagos, sí
        mockMvc.perform(delete(URL + "/deudas/{id}", id)).andExpect(status().isConflict());
        Long otra = deuda("Laboratorio Andino", "2026-06-05", "50");
        mockMvc.perform(delete(URL + "/deudas/{id}", otra)).andExpect(status().isNoContent());
        mockMvc.perform(get(URL + "/deudas")).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void validaLosDatos() throws Exception {
        postJson(URL + "/deudas", """
                {"proveedor": " ", "monto": 10}""").andExpect(status().isBadRequest());
        postJson(URL + "/deudas", """
                {"proveedor": "Distribuidora Pérez", "monto": 0}""").andExpect(status().isBadRequest());
        postJson(URL + "/deudas", """
                {"proveedor": "Distribuidora Pérez", "monto": 10.123}""").andExpect(status().isBadRequest());
        Long id = deuda("Distribuidora Pérez", "2026-06-01", "100");
        postJson(URL + "/deudas/" + id + "/pagos", """
                {"monto": 10, "medio": "YAPE", "fecha": "2026-06-16"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha del pago no puede ser futura"));
        postJson(URL + "/deudas/" + id + "/pagos", """
                {"monto": 10}""").andExpect(status().isBadRequest());
        mockMvcSinSesion.perform(get(URL + "/deudas")).andExpect(status().isUnauthorized());
    }
}
