package com.evamoreno.inventario.stock;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * En los insumos médicos el vencimiento se expresa como mes y año ("03/2027"): el producto se puede usar
 * durante todo ese mes. Por eso toda fecha de vencimiento se guarda como el ÚLTIMO día de su mes,
 * sin importar el día con el que llegue.
 */
public final class Vencimientos {

    private Vencimientos() {
    }

    /** 2027-03-01, 2027-03-15 y 2027-03-31 se convierten en 2027-03-31. Null se queda en null. */
    public static LocalDate finDeMes(LocalDate fecha) {
        return fecha == null ? null : YearMonth.from(fecha).atEndOfMonth();
    }
}
