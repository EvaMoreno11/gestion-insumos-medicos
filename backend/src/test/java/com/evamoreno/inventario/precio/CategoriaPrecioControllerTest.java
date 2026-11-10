package com.evamoreno.inventario.precio;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.evamoreno.inventario.IntegracionTest;

class CategoriaPrecioControllerTest extends IntegracionTest {

    private static final String URL = "/api/categorias-precio";

    @Test
    void vieneConLasCategoriasABYC() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].letra", contains("A", "B", "C")))
                .andExpect(jsonPath("$[*].porcentaje", contains(10.0, 15.0, 20.0)));
    }

    @Test
    void sePuedeAgregarOtraCategoriaYCambiarSuPorcentaje() throws Exception {
        Long id = crear(URL, """
                {"letra": " d ", "porcentaje": 25}""");

        mockMvc.perform(put(URL + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"letra": "D", "porcentaje": 27.5}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.letra").value("D"))
                .andExpect(jsonPath("$.porcentaje").value(27.5));

        mockMvc.perform(get(URL)).andExpect(jsonPath("$[*].letra", contains("A", "B", "C", "D")));
    }

    @Test
    void noSeRepitenNiLaLetraNiElPorcentaje() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"letra": "a", "porcentaje": 30}"""))
                .andExpect(status().isConflict());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"letra": "X", "porcentaje": 15.00}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La categoría B ya usa el 15 %"));
    }

    @Test
    void elProductoMuestraSuPrecioEnCadaCategoria() throws Exception {
        Long id = crear("/api/productos", """
                {"nombre": "Aerocámara pediátrica", "marca": "Marca A", "categoriaId": %d,
                 "presentacion": "Unidad", "unidadMedida": "Unidad", "costo": 4.80}"""
                .formatted(idCategoria("Otros")));

        mockMvc.perform(get("/api/productos/{id}", id))
                .andExpect(jsonPath("$.costo").value(4.8))
                .andExpect(jsonPath("$.precios[*].letra", contains("A", "B", "C")))
                .andExpect(jsonPath("$.precios[*].precio", contains(5.28, 5.52, 5.76)));

        mockMvc.perform(get("/api/productos").param("buscar", "aerocamara"))
                .andExpect(jsonPath("$.contenido[0].costo").value(4.8));
    }

    @Test
    void unProductoSinCostoNoTienePrecios() throws Exception {
        Long id = crear("/api/productos", """
                {"nombre": "Producto sin costo", "marca": "Marca A", "categoriaId": %d,
                 "presentacion": "Unidad", "unidadMedida": "Unidad"}""".formatted(idCategoria("Otros")));

        mockMvc.perform(get("/api/productos/{id}", id))
                .andExpect(jsonPath("$.costo").doesNotExist())
                .andExpect(jsonPath("$.precios", empty()));
    }

    @Test
    void elCostoNoPuedeSerNegativo() throws Exception {
        mockMvc.perform(post("/api/productos").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre": "Producto", "marca": "Marca A", "categoriaId": %d,
                         "presentacion": "Unidad", "unidadMedida": "Unidad", "costo": -1}"""
                        .formatted(idCategoria("Otros"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.costo").value("Debe ser 0 o más"));
    }
}
