package com.evamoreno.inventario.stock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Algoritmo FEFO (First Expired, First Out): decide de qué lotes sacar las unidades,
 * empezando por el que vence antes. No modifica nada; solo calcula el plan.
 *
 * <pre>
 * Lotes: L1 vence 03/2027 (4 u.), L2 vence 01/2028 (10 u.)   Pedido: 6 u.
 * Plan:  4 del L1 + 2 del L2   (faltante = 0)
 * </pre>
 *
 * Entre lotes que vencen el MISMO mes da igual cuál sale primero, así que se busca partir lo menos
 * posible: si un lote alcanza solo, sale todo de ese (el más chico que alcance, para no abrir una caja
 * cuando bastan las sueltas); si ninguno alcanza, se empieza por el más grande.
 *
 * <pre>
 * Mismo vencimiento: 3 sueltas (L1) y una caja de 100 (L2)
 * Pedido 100 → la caja entera (L2)          Pedido 2   → 2 de las sueltas (L1)
 * Pedido 10  → 10 de la caja (L2)           Pedido 102 → la caja (L2) + 2 sueltas (L1)
 * </pre>
 *
 * Lo usan las salidas, el despacho de pedidos y las correcciones de un pedido despachado.
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
        int inicio = 0;
        while (inicio < lotesEnOrdenFefo.size() && pendiente > 0) {
            // Los lotes que vencen el mismo mes que el primero que queda (el orden FEFO los deja juntos)
            int fin = inicio;
            while (fin < lotesEnOrdenFefo.size() && Objects.equals(lotesEnOrdenFefo.get(fin).getFechaVencimiento(),
                    lotesEnOrdenFefo.get(inicio).getFechaVencimiento())) {
                fin++;
            }
            List<Lote> mismoVencimiento = new ArrayList<>(lotesEnOrdenFefo.subList(inicio, fin));
            mismoVencimiento.removeIf(l -> l.getCantidadActual() <= 0);
            while (pendiente > 0 && !mismoVencimiento.isEmpty()) {
                Lote lote = elegir(mismoVencimiento, pendiente);
                int tomar = Math.min(pendiente, lote.getCantidadActual());
                asignaciones.add(new Asignacion(lote, tomar));
                pendiente -= tomar;
                mismoVencimiento.remove(lote);
            }
            inicio = fin;
        }
        return new PlanFefo(List.copyOf(asignaciones), pendiente);
    }

    /**
     * Entre lotes del mismo vencimiento: el más chico que alcanza solo; si ninguno alcanza, el más grande.
     * Si empatan, el primero de la lista (el que se registró antes).
     */
    private static Lote elegir(List<Lote> mismoVencimiento, int pendiente) {
        Lote masChicoQueAlcanza = null;
        Lote masGrande = null;
        for (Lote lote : mismoVencimiento) {
            int hay = lote.getCantidadActual();
            if (hay >= pendiente && (masChicoQueAlcanza == null || hay < masChicoQueAlcanza.getCantidadActual())) {
                masChicoQueAlcanza = lote;
            }
            if (masGrande == null || hay > masGrande.getCantidadActual()) {
                masGrande = lote;
            }
        }
        return masChicoQueAlcanza != null ? masChicoQueAlcanza : masGrande;
    }

    public boolean alcanza() {
        return faltante == 0;
    }

    public int cantidadCubierta() {
        return asignaciones.stream().mapToInt(Asignacion::cantidad).sum();
    }
}
