package com.evamoreno.inventario.producto;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.evamoreno.inventario.IntegracionTest;

class ProductoControllerTest extends IntegracionTest {

    private static final String URL = "/api/productos";

    /** JSON de un producto; los campos opcionales se omiten. */
    private String json(String nombre, String marca, String categoria, String presentacion, String extra) {
        return """
                {"nombre": "%s", "marca": "%s", "categoriaId": %d,
                 "presentacion": "%s", "unidadMedida": "Caja" %s}"""
                .formatted(nombre, marca, idCategoria(categoria), presentacion, extra.isEmpty() ? "" : ", " + extra);
    }

    private Long crearProducto(String nombre, String marca, String categoria, String presentacion, String extra)
            throws Exception {
        return crear(URL, json(nombre, marca, categoria, presentacion, extra));
    }

    private ResultActions enviar(String json) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ------------------------------------------------------------------ crear

    @Test
    void creaProductoConCodigoAutomaticoYDatosLimpios() throws Exception {
        enviar(json("  Jeringa   descartable 5 ml ", "Marca A", "Jeringas", "Caja x 100",
                "\"codigosBarras\": [\"2000000000039\", \" 2000000000039 \"]"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/productos/\\d+")))
                .andExpect(jsonPath("$.codigo", matchesPattern("JER-\\d{5}")))
                .andExpect(jsonPath("$.nombre").value("Jeringa descartable 5 ml"))
                .andExpect(jsonPath("$.categoria").value("Jeringas"))
                .andExpect(jsonPath("$.stockMinimo").value(0))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.codigosBarras", containsInAnyOrder("2000000000039")));
    }

    @Test
    void usaElCodigoManualEnMayusculasYNoPermiteRepetirlo() throws Exception {
        enviar(json("Guantes de látex talla M", "Marca B", "Guantes", "Caja x 100", "\"codigo\": \"gua-latex-m\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("GUA-LATEX-M"));

        enviar(json("Guantes de látex talla S", "Marca B", "Guantes", "Caja x 100", "\"codigo\": \"GUA-LATEX-M\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un producto con el código GUA-LATEX-M"));
    }

    @Test
    void rechazaElMismoProductoAunqueSeEscribaDistinto() throws Exception {
        crearProducto("Gasa estéril 10x10 cm", "Marca D", "Gasas y apósitos", "Paquete x 10", "");

        enviar(json("GASA ESTERIL  10 x 10 CM", "marca d", "Gasas y apósitos", "paquete x10", ""))
                .andExpect(status().isConflict());
    }

    @Test
    void otraMarcaOPresentacionEsOtroProducto() throws Exception {
        crearProducto("Jeringa descartable 5 ml", "Marca A", "Jeringas", "Caja x 100", "");
        crearProducto("Jeringa descartable 5 ml", "Marca B", "Jeringas", "Caja x 100", "");
        crearProducto("Jeringa descartable 5 ml", "Marca A", "Jeringas", "Caja x 50", "");
    }

    @Test
    void validaLosCamposObligatorios() throws Exception {
        enviar("""
                {"nombre": "", "marca": "Marca A", "presentacion": "Caja x 100", "unidadMedida": "Caja",
                 "stockMinimo": -1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errores.categoriaId").value("La categoría es obligatoria"))
                .andExpect(jsonPath("$.errores.stockMinimo").value("Debe ser 0 o más"));
    }

    @Test
    void responde404SiLaCategoriaNoExiste() throws Exception {
        enviar("""
                {"nombre": "Producto", "marca": "Marca A", "categoriaId": 999999,
                 "presentacion": "Unidad", "unidadMedida": "Unidad"}""")
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ buscar

    @Test
    void buscaSinImportarMayusculasTildesNiEspacios() throws Exception {
        crearProducto("Jeringa descartable 5 ml con aguja 21G", "Marca A", "Jeringas", "Caja x 100", "");
        crearProducto("Jeringa descartable 3 ml con aguja 25G", "Marca A", "Jeringas", "Caja x 100", "");
        crearProducto("Jeringa descartable 20 ml con aguja 21G", "Marca B", "Jeringas", "Caja x 50", "");
        crearProducto("Apósito transparente 6 x 7 cm", "Marca A", "Gasas y apósitos", "Caja x 50", "");

        // El "5" debe ser el número completo: no coincide con "25G" ni con "Caja x 50"
        mockMvc.perform(get(URL).param("buscar", "JER 5ML"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Jeringa descartable 5 ml con aguja 21G"));

        mockMvc.perform(get(URL).param("buscar", "aposito"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Apósito transparente 6 x 7 cm"));

        mockMvc.perform(get(URL).param("buscar", "jeringa"))
                .andExpect(jsonPath("$.totalElementos").value(3));
    }

    @Test
    void unaLetraSueltaEsUnaTallaYNoElInicioDeOtraPalabra() throws Exception {
        crearProducto("Guantes de látex talla M", "Marca B", "Guantes", "Caja x 100", "");
        crearProducto("Guantes de látex talla S", "Marca B", "Guantes", "Caja x 100", "");

        // La "m" no debe coincidir con "Marca"
        mockMvc.perform(get(URL).param("buscar", "guantes m"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Guantes de látex talla M"));
    }

    @Test
    void filtraPorCategoriaYPagina() throws Exception {
        crearProducto("Aguja hipodérmica 21G", "Marca A", "Agujas", "Caja x 100", "");
        crearProducto("Aguja hipodérmica 23G", "Marca A", "Agujas", "Caja x 100", "");
        crearProducto("Aguja hipodérmica 25G", "Marca A", "Agujas", "Caja x 100", "");
        crearProducto("Guantes de nitrilo talla M", "Marca C", "Guantes", "Caja x 100", "");

        mockMvc.perform(get(URL).param("categoriaId", idCategoria("Agujas").toString())
                        .param("tamano", "2").param("pagina", "1"))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Aguja hipodérmica 25G"));
    }

    @Test
    void unProductoDesactivadoNoApareceEnLaBusquedaNormal() throws Exception {
        Long id = crearProducto("Sonda Foley 2 vías 16 Fr", "Marca C", "Catéteres y sondas", "Unidad", "");

        mockMvc.perform(patch(URL + "/{id}/desactivar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(get(URL).param("buscar", "sonda foley"))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(get(URL).param("buscar", "sonda foley").param("incluirInactivos", "true"))
                .andExpect(jsonPath("$.totalElementos").value(1));

        mockMvc.perform(patch(URL + "/{id}/activar", id))
                .andExpect(jsonPath("$.activo").value(true));
    }

    // ------------------------------------------------------------------ códigos de barras

    @Test
    void buscaPorCodigoDeBarrasYNoPermiteUsarloEnDosProductos() throws Exception {
        crearProducto("Gasa estéril 5 x 5 cm", "Marca D", "Gasas y apósitos", "Paquete x 10",
                "\"codigosBarras\": [\"2000000000084\"]");

        mockMvc.perform(get(URL + "/codigo-barras/{codigo}", "2000000000084"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Gasa estéril 5 x 5 cm"));

        enviar(json("Gasa estéril 10 x 10 cm", "Marca D", "Gasas y apósitos", "Paquete x 10",
                "\"codigosBarras\": [\"2000000000084\"]"))
                .andExpect(status().isConflict());

        mockMvc.perform(get(URL + "/codigo-barras/{codigo}", "2999999999999"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ actualizar

    @Test
    void actualizaConservandoElCodigoYReemplazandoCodigosDeBarras() throws Exception {
        Long id = crearProducto("Venda elástica 4 x 5 yardas", "Marca D", "Esparadrapos y vendas", "Unidad",
                "\"codigosBarras\": [\"2000000000107\", \"2000000000114\"]");
        String codigo = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(get(URL + "/{id}", id)).andReturn().getResponse().getContentAsString(), "$.codigo");

        mockMvc.perform(put(URL + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Venda elástica 6 x 5 yardas", "Marca D", "Esparadrapos y vendas", "Unidad",
                                "\"codigo\": \"\", \"stockMinimo\": 10, "
                                        + "\"codigosBarras\": [\"2000000000114\", \"2000000000121\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.nombre").value("Venda elástica 6 x 5 yardas"))
                .andExpect(jsonPath("$.stockMinimo").value(10))
                .andExpect(jsonPath("$.codigosBarras", containsInAnyOrder("2000000000114", "2000000000121")));

        // El nombre nuevo también se refleja en el buscador
        mockMvc.perform(get(URL).param("buscar", "venda 6"))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void noPermiteActualizarAUnProductoQueYaExiste() throws Exception {
        crearProducto("Guantes de látex talla S", "Marca B", "Guantes", "Caja x 100", "");
        Long id = crearProducto("Guantes de látex talla M", "Marca B", "Guantes", "Caja x 100", "");

        mockMvc.perform(put(URL + "/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Guantes de latex talla S", "Marca B", "Guantes", "Caja x 100", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", endsWith(")")));
    }

    @Test
    void responde404SiElProductoNoExiste() throws Exception {
        mockMvc.perform(get(URL + "/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el producto con id 999999"));
    }
}
