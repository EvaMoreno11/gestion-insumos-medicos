package com.evamoreno.inventario;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;

import com.evamoreno.inventario.categoria.CategoriaRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Base de los tests de integración: la app completa contra un PostgreSQL real (Testcontainers).
 * Cada test corre en una transacción que se revierte al final, así no se afectan entre sí.
 *
 * <p>{@link #mockMvc} hace cada petición con una sesión iniciada y el token CSRF, como la app real.
 * Para probar la seguridad (sin sesión, sin token) se usa {@link #mockMvcSinSesion}.</p>
 *
 * <p>El token CSRF se envía igual que el navegador: el mismo valor en la cookie XSRF-TOKEN y en la cabecera
 * X-XSRF-TOKEN. No se usa el ayudante {@code csrf()} de spring-security-test porque reemplaza el repositorio
 * de tokens del filtro compartido y ese cambio afecta a los tests siguientes.</p>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
public abstract class IntegracionTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    protected CategoriaRepository categoriaRepository;

    private static final String TOKEN_CSRF_PRUEBA = "token-csrf-de-prueba";

    /** Peticiones con sesión iniciada (usuario "prueba") y token CSRF. */
    protected MockMvc mockMvc;

    /** Peticiones sin sesión ni token, pasando por toda la seguridad. */
    protected MockMvc mockMvcSinSesion;

    @BeforeEach
    void prepararMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user("prueba"))
                        .cookie(new Cookie("XSRF-TOKEN", TOKEN_CSRF_PRUEBA))
                        .header("X-XSRF-TOKEN", TOKEN_CSRF_PRUEBA))
                .build();
        mockMvcSinSesion = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .build();
    }

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
