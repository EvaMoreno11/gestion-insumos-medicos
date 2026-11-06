package com.evamoreno.inventario.stock;

import java.util.ArrayList;
import java.util.List;

/**
 * Algoritmo FEFO (First Expired, First Out): decide de qué lotes sacar las unidades,
 * empezando por el que vence antes. No modifica nada; solo calcula el plan.
 *
 * <pre>
 * Lotes: L1 vence 03/2027 (4 u.), L2 vence 01/2028 (10 u.)   Pedido: 6 u.
 * Plan:  4 del L1 + 2 del L2   (faltante = 0)
 * </pre>
 *
 * Lo usan las salidas y, en la Fase 3, la verificación de disponibilidad de pedidos.
 *
 * @param asignaciones cuántas unidades se toman de cada lote
 * @param faltante     unidades que no se pudieron cubrir (0 si alcanza)
 */
public record PlanFefo(List<Asignacion> asignaciones, int faltante) {

    public record Asignacion(Lote lote, int cantidad) {
    }

    /**
     * @param lotesEnOrdenFefo lotes disponibles (no vencidos), ya ordenados por vencimiento
     * @param cantidad         unidades pedidas (mayor que 0)
     */
    public static PlanFefo calcular(List<Lote> lotesEnOrdenFefo, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que 0");
        }
        List<Asignacion> asignaciones = new ArrayList<>();
        int pendiente = cantidad;
        for (Lote lote : lotesEnOrdenFefo) {
            if (pendiente == 0) {
                break;
            }
            int tomar = Math.min(pendiente, lote.getCantidadActual());
            if (tomar > 0) {
                asignaciones.add(new Asignacion(lote, tomar));
                pendiente -= tomar;
            }
        }
        return new PlanFefo(List.copyOf(asignaciones), pendiente);
    }

    public boolean alcanza() {
        return faltante == 0;
    }

    public int cantidadCubierta() {
        return asignaciones.stream().mapToInt(Asignacion::cantidad).sum();
    }
}
