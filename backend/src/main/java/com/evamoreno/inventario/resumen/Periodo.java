package com.evamoreno.inventario.resumen;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.evamoreno.inventario.common.SolicitudInvalidaException;

/**
 * Periodo del resumen (fechas inclusive) y el periodo con el que se compara.
 *
 * <ul>
 * <li>Este mes (1 al día de hoy) se compara con los mismos días del mes pasado.</li>
 * <li>El mes pasado (completo) se compara con el mes anterior completo.</li>
 * <li>Este año (1 de enero a hoy) se compara con los mismos días del año pasado.</li>
 * <li>Un rango se compara con un rango de igual largo justo antes.</li>
 * </ul>
 */
public record Periodo(LocalDate desde, LocalDate hasta, LocalDate anteriorDesde, LocalDate anteriorHasta,
        String nombre, String nombreAnterior) {

    static final int MAXIMO_DIAS = 3 * 366;

    public enum Tipo {
        MES, MES_ANTERIOR, ANIO, RANGO
    }

    static Periodo de(Tipo tipo, LocalDate desde, LocalDate hasta, LocalDate hoy) {
        return switch (tipo) {
            case MES -> {
                LocalDate inicio = hoy.withDayOfMonth(1);
                LocalDate inicioAnterior = inicio.minusMonths(1);
                LocalDate finAnterior = inicioAnterior.withDayOfMonth(
                        Math.min(hoy.getDayOfMonth(), inicioAnterior.lengthOfMonth()));
                yield new Periodo(inicio, hoy, inicioAnterior, finAnterior, "este mes", "el mes pasado");
            }
            case MES_ANTERIOR -> {
                LocalDate inicio = hoy.withDayOfMonth(1).minusMonths(1);
                LocalDate inicioAnterior = inicio.minusMonths(1);
                yield new Periodo(inicio, inicio.withDayOfMonth(inicio.lengthOfMonth()), inicioAnterior,
                        inicioAnterior.withDayOfMonth(inicioAnterior.lengthOfMonth()), "el mes pasado",
                        "el mes anterior");
            }
            case ANIO -> {
                LocalDate inicio = hoy.withDayOfYear(1);
                yield new Periodo(inicio, hoy, inicio.minusYears(1), hoy.minusYears(1), "este año", "el año pasado");
            }
            case RANGO -> {
                if (desde == null || hasta == null) {
                    throw new SolicitudInvalidaException("Indica las fechas desde y hasta");
                }
                if (hasta.isBefore(desde)) {
                    throw new SolicitudInvalidaException("La fecha final no puede ser anterior a la inicial");
                }
                long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
                if (dias > MAXIMO_DIAS) {
                    throw new SolicitudInvalidaException("El rango puede ser de hasta 3 años");
                }
                yield new Periodo(desde, hasta, desde.minusDays(dias), desde.minusDays(1), "el periodo",
                        "el periodo anterior");
            }
        };
    }
}
