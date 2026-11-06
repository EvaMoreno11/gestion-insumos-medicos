package com.evamoreno.inventario.stock;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Un lote tal como se muestra al usuario.
 *
 * @param diasParaVencer días que faltan para el vencimiento (negativo si ya venció; null si no vence)
 */
public record LoteResponse(
        Long id,
        String numeroLote,
        LocalDate fechaVencimiento,
        int cantidad,
        boolean vencido,
        Long diasParaVencer) {

    public static LoteResponse de(Lote lote, LocalDate hoy) {
        Long dias = lote.getFechaVencimiento() == null
                ? null
                : ChronoUnit.DAYS.between(hoy, lote.getFechaVencimiento());
        return new LoteResponse(lote.getId(), lote.getNumeroLote(), lote.getFechaVencimiento(),
                lote.getCantidadActual(), lote.estaVencido(hoy), dias);
    }
}
