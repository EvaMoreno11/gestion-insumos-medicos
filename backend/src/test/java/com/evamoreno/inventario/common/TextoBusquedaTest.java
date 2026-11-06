package com.evamoreno.inventario.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.api.Test;

class TextoBusquedaTest {

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource(delimiter = '|', textBlock = """
            Jeringa 5ml                       | jeringa 5 ml
            JERINGA   5 ML                    | jeringa 5 ml
            Gasa Estéril 10x10 cm             | gasa esteril 10 x 10 cm
            Aguja 21G x 1½"                   | aguja 21 g x 1 1/2
            Guantes quirúrgicos talla 7 1/2   | guantes quirurgicos talla 7 1/2
            Esparadrapo 2,5 cm                | esparadrapo 2.5 cm
            Caja x100                         | caja x 100
            Pañal                             | panal
            c/ aguja. (estéril)               | c aguja esteril
            JER-00012                         | jer 00012
            """)
    void normaliza(String entrada, String esperado) {
        assertThat(TextoBusqueda.normalizar(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void textoVacioQuedaVacio(String entrada) {
        assertThat(TextoBusqueda.normalizar(entrada)).isEmpty();
        assertThat(TextoBusqueda.tokens(entrada)).isEmpty();
    }

    @Test
    void tokensSinRepetir() {
        assertThat(TextoBusqueda.tokens("Jer 5ml jer")).containsExactly("jer", "5", "ml");
    }

    @Test
    void limpiarConservaMayusculasYTildes() {
        assertThat(TextoBusqueda.limpiar("  Gasa   Estéril  ")).isEqualTo("Gasa Estéril");
    }
}
