package com.evamoreno.inventario.precio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PreciosTest {

    @ParameterizedTest(name = "costo {0} + {1} % = {2}")
    @CsvSource({
            "4.80,   10, 5.28",
            "4.80,   15, 5.52",
            "4.80,   20, 5.76",
            "8.50,   15, 9.78",    // 9.775 se redondea hacia arriba
            "0.7456, 10, 0.82",    // costo con 4 decimales
            "3.8333, 20, 4.60",
            "7,      12.5, 7.88",  // porcentaje con decimales: 7.875
            "10,     0, 10.00",
    })
    void calculaElPrecioRedondeadoADosDecimales(String costo, String porcentaje, String esperado) {
        assertThat(Precios.precio(new BigDecimal(costo), new BigDecimal(porcentaje)))
                .isEqualByComparingTo(esperado);
    }

    @Test
    void sinCostoOSinPorcentajeNoHayPrecio() {
        assertThat(Precios.precio(null, BigDecimal.TEN)).isNull();
        assertThat(Precios.precio(BigDecimal.TEN, null)).isNull();
    }
}
