package com.evamoreno.inventario.precio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculo del precio de venta: costo + porcentaje, redondeado a 2 decimales.
 *
 * <pre>
 * costo 4.80, porcentaje 15  →  4.80 × 1.15 = 5.52
 * </pre>
 */
public final class Precios {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private Precios() {
    }

    /** @return el precio, o null si el producto no tiene costo o no hay porcentaje definido */
    public static BigDecimal precio(BigDecimal costo, BigDecimal porcentaje) {
        if (costo == null || porcentaje == null) {
            return null;
        }
        BigDecimal factor = BigDecimal.ONE.add(porcentaje.divide(CIEN));
        return costo.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
