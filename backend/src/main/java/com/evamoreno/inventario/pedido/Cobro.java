package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Situación del cobro de un pedido despachado: cuánto falta, cuándo vence y en qué estado está.
 *
 * @param vence null si aún no se anotó el plazo
 * @param saldo lo que falta cobrar; negativo si el cliente pagó de más (saldo a favor)
 * @param dias  días que faltan para que venza (negativo: días de atraso); null si no hay plazo o ya se pagó
 */
public record Cobro(BigDecimal total, BigDecimal pagado, BigDecimal saldo, Integer plazoDias, LocalDate vence,
        Estado estado, Long dias) {

    public enum Estado {
        /** No falta nada por cobrar. */
        PAGADO,
        /** Tiene plazo y todavía no vence. */
        POR_COBRAR,
        /** Pasó el plazo y aún se debe. */
        VENCIDO,
        /** Se debe, pero nadie anotó todavía cómo paga (contado o crédito). */
        SIN_PLAZO,
        /** Pagó más que el total (p. ej. tras una devolución): el saldo, negativo, es a favor del cliente. */
        A_FAVOR
    }

    /**
     * @param plazoDias  null = sin plazo; 0 = contado (vence el mismo día del despacho); N = crédito a N días
     * @param despachado día del despacho
     */
    public static Cobro de(BigDecimal total, BigDecimal pagado, Integer plazoDias, LocalDate despachado,
            LocalDate hoy) {
        BigDecimal saldo = total.subtract(pagado);
        LocalDate vence = plazoDias == null ? null : despachado.plusDays(plazoDias);
        if (saldo.signum() < 0) {
            return new Cobro(total, pagado, saldo, plazoDias, vence, Estado.A_FAVOR, null);
        }
        if (saldo.signum() == 0) {
            return new Cobro(total, pagado, BigDecimal.ZERO.setScale(2), plazoDias, vence, Estado.PAGADO, null);
        }
        if (vence == null) {
            return new Cobro(total, pagado, saldo, null, null, Estado.SIN_PLAZO, null);
        }
        long dias = ChronoUnit.DAYS.between(hoy, vence);
        return new Cobro(total, pagado, saldo, plazoDias, vence, dias < 0 ? Estado.VENCIDO : Estado.POR_COBRAR, dias);
    }
}
