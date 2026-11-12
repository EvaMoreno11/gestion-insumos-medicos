package com.evamoreno.inventario.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;

import jakarta.servlet.http.Cookie;

/**
 * Prueba la seguridad como la usa la app: primero consulta el estado (que entrega el token CSRF en una cookie)
 * y luego envía ese token en la cabecera X-XSRF-TOKEN de cada operación que cambia datos.
 */
class AuthControllerTest extends IntegracionTest {

    @Autowired
    private UsuarioRepository usuarios;

    /** Token CSRF y su cookie, como los obtiene el navegador al abrir la app. */
    private record Csrf(String token, Cookie cookie) {
    }

    private Csrf csrf() throws Exception {
        MvcResult r = mockMvcSinSesion.perform(get("/api/auth/estado")).andExpect(status().isOk()).andReturn();
        Cookie cookie = r.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("el estado debe entregar la cookie XSRF-TOKEN").isNotNull();
        return new Csrf(cookie.getValue(), cookie);
    }

    private ResultActions postSinSesion(String url, String json, Csrf csrf) throws Exception {
        return mockMvcSinSesion.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)
                .header("X-XSRF-TOKEN", csrf.token()).cookie(csrf.cookie()));
    }

    private void crearCuenta(String usuario, String clave) throws Exception {
        postSinSesion("/api/auth/configurar", """
                {"usuario": "%s", "clave": "%s"}""".formatted(usuario, clave), csrf())
                .andExpect(status().isCreated());
    }

    private ResultActions login(String usuario, String clave, boolean recordar) throws Exception {
        return postSinSesion("/api/auth/login", """
                {"usuario": "%s", "clave": "%s", "recordar": %s}""".formatted(usuario, clave, recordar), csrf());
    }

    // ------------------------------------------------------------------ la API está protegida

    @Test
    void sinSesionLaApiResponde401ConUnMensajeClaro() throws Exception {
        mockMvcSinSesion.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Inicia sesión para continuar."));
        mockMvcSinSesion.perform(get("/api/pedidos/1/nota.pdf")).andExpect(status().isUnauthorized());
        mockMvcSinSesion.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenCsrfNoSePuedeCambiarNada() throws Exception {
        crearCuenta("tienda", "clave-segura-1");
        MockHttpSession sesion = (MockHttpSession) login("tienda", "clave-segura-1", false)
                .andReturn().getRequest().getSession(false);

        // Con sesión pero sin el token: así se vería un ataque desde otra página web
        mockMvcSinSesion.perform(post("/api/categorias").session(sesion).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Intento"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Recarga la página")));
    }

    // ------------------------------------------------------------------ primera cuenta

    @Test
    void laPrimeraCuentaSeCreaUnaSolaVez() throws Exception {
        mockMvcSinSesion.perform(get("/api/auth/estado"))
                .andExpect(jsonPath("$.autenticado").value(false))
                .andExpect(jsonPath("$.requiereConfiguracion").value(true))
                .andExpect(jsonPath("$.requiereCodigo").value(false));

        crearCuenta("Tienda", "clave-segura-1");
        assertThat(usuarios.findByUsuarioIgnoreCase("tienda").orElseThrow().getClaveHash())
                .startsWith("{bcrypt}")
                .doesNotContain("clave-segura-1");

        mockMvcSinSesion.perform(get("/api/auth/estado")).andExpect(jsonPath("$.requiereConfiguracion").value(false));
        postSinSesion("/api/auth/configurar", """
                {"usuario": "intruso", "clave": "otra-clave-1"}""", csrf())
                .andExpect(status().isConflict());
    }

    @Test
    void laContrasenaDebeTenerAlMenos8Caracteres() throws Exception {
        postSinSesion("/api/auth/configurar", """
                {"usuario": "tienda", "clave": "corta"}""", csrf())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.clave").value("Debe tener al menos 8 caracteres"));
    }

    // ------------------------------------------------------------------ inicio de sesión

    @Test
    void conUsuarioYContrasenaCorrectosSeEntraYSeSale() throws Exception {
        crearCuenta("Tienda", "clave-segura-1");

        MvcResult entrada = login("TIENDA", "clave-segura-1", false) // el usuario no distingue mayúsculas
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.usuario").value("Tienda"))
                .andExpect(cookie().doesNotExist(SeguridadConfig.COOKIE_RECORDAR))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) entrada.getRequest().getSession(false);

        mockMvcSinSesion.perform(get("/api/productos").session(sesion)).andExpect(status().isOk());
        mockMvcSinSesion.perform(get("/api/auth/estado").session(sesion))
                .andExpect(jsonPath("$.autenticado").value(true));

        Csrf csrf = csrf();
        mockMvcSinSesion.perform(post("/api/auth/salir").session(sesion)
                        .header("X-XSRF-TOKEN", csrf.token()).cookie(csrf.cookie()))
                .andExpect(status().isNoContent());
        mockMvcSinSesion.perform(get("/api/productos").session(sesion)).andExpect(status().isUnauthorized());
    }

    @Test
    void conContrasenaIncorrectaNoSeEntraNiSeRevelaQueFallo() throws Exception {
        crearCuenta("tienda", "clave-segura-1");

        login("tienda", "equivocada", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
        login("no-existe", "clave-segura-1", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void despuesDe5IntentosFallidosLaCuentaSeBloqueaUnRato() throws Exception {
        crearCuenta("tienda", "clave-segura-1");

        for (int i = 1; i <= 4; i++) {
            login("tienda", "equivocada-" + i, false).andExpect(status().isUnauthorized());
        }
        login("tienda", "equivocada-5", false)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail", containsString("Espera 15 minutos")));

        // Bloqueada: ni siquiera con la contraseña correcta
        login("tienda", "clave-segura-1", false).andExpect(status().isTooManyRequests());
    }

    @Test
    void recordarmeDejaUnaCookieDe30DiasQueSirveSinSesion() throws Exception {
        crearCuenta("tienda", "clave-segura-1");

        Cookie recordar = login("tienda", "clave-segura-1", true)
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly(SeguridadConfig.COOKIE_RECORDAR, true))
                .andExpect(cookie().maxAge(SeguridadConfig.COOKIE_RECORDAR, 30 * 24 * 60 * 60))
                .andReturn().getResponse().getCookie(SeguridadConfig.COOKIE_RECORDAR);

        // Sin sesión (p. ej. el servidor se reinició), la cookie basta para entrar
        mockMvcSinSesion.perform(get("/api/productos").cookie(recordar)).andExpect(status().isOk());
    }

    @Test
    void cambiarLaContrasenaAnulaLasSesionesRecordadasDeOtrosDispositivos() throws Exception {
        crearCuenta("tienda", "clave-segura-1");
        Cookie otroCelular = login("tienda", "clave-segura-1", true)
                .andReturn().getResponse().getCookie(SeguridadConfig.COOKIE_RECORDAR);
        MockHttpSession sesion = (MockHttpSession) login("tienda", "clave-segura-1", false)
                .andReturn().getRequest().getSession(false);

        Csrf csrf = csrf();
        mockMvcSinSesion.perform(put("/api/auth/clave").session(sesion).contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", csrf.token()).cookie(csrf.cookie())
                        .content("""
                                {"actual": "clave-segura-1", "nueva": "clave-nueva-2"}"""))
                .andExpect(status().isNoContent());

        mockMvcSinSesion.perform(get("/api/productos").cookie(otroCelular)).andExpect(status().isUnauthorized());
        login("tienda", "clave-segura-1", false).andExpect(status().isUnauthorized());
        login("tienda", "clave-nueva-2", false).andExpect(status().isOk());
    }

    @Test
    void elEstadoMuestraElNombreDelNegocioParaLaPantallaDeEntrada() throws Exception {
        mockMvc.perform(put("/api/negocio").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre": "Distribuidora Demo"}""")).andExpect(status().isOk());

        mockMvcSinSesion.perform(get("/api/auth/estado")).andExpect(jsonPath("$.negocio").value("Distribuidora Demo"));
    }
}
