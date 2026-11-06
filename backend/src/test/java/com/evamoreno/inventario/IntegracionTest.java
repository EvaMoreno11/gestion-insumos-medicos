package com.evamoreno.inventario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.categoria.CategoriaRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Base de los tests de integración: la app completa contra un PostgreSQL real (Testcontainers).
 * Cada test corre en una transacción que se revierte al final, así no se afectan entre sí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class IntegracionTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected CategoriaRepository categoriaRepository;

    protected Long idCategoria(String nombre) {
        return categoriaRepository.findByNombreIgnoreCase(nombre).orElseThrow().getId();
    }

    /** Hace un POST con JSON, exige 201 Created y devuelve el id creado. */
    protected Long crear(String url, String json) throws Exception {
        String respuesta = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }
}
