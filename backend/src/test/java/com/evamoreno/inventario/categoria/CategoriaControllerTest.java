package com.evamoreno.inventario.categoria;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.evamoreno.inventario.IntegracionTest;

class CategoriaControllerTest extends IntegracionTest {

    @Test
    void listaLasCategoriasInicialesEnOrdenAlfabetico() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(11)))
                .andExpect(jsonPath("$[0].nombre").value("Agujas"));
    }

    @Test
    void creaYRenombraUnaCategoria() throws Exception {
        Long id = crear("/api/categorias", """
                {"nombre": "  Material   de curación "}""");

        mockMvc.perform(put("/api/categorias/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Curación"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Curación"));
    }

    @Test
    void rechazaCategoriaDuplicadaSinImportarMayusculas() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "GUANTES"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe la categoría \"Guantes\""));
    }

    @Test
    void rechazaNombreVacio() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "  "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").value("El nombre es obligatorio"));
    }

    @Test
    void noEliminaCategoriaConProductos() throws Exception {
        crear("/api/productos", """
                {"nombre": "Gasa estéril 10 x 10 cm", "marca": "Marca D", "categoriaId": %d,
                 "presentacion": "Paquete x 10", "unidadMedida": "Paquete"}"""
                .formatted(idCategoria("Gasas y apósitos")));

        mockMvc.perform(delete("/api/categorias/{id}", idCategoria("Gasas y apósitos")))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminaCategoriaSinProductos() throws Exception {
        Long id = crear("/api/categorias", """
                {"nombre": "Temporal"}""");

        mockMvc.perform(delete("/api/categorias/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/categorias/{id}", id)).andExpect(status().isNotFound());
    }
}
