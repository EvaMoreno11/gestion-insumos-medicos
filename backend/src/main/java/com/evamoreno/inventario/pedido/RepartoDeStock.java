package com.evamoreno.inventario.pedido;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reparte el stock disponible entre los pedidos pendientes, por orden de llegada:
 * el pedido más antiguo "separa" primero lo que necesita y los siguientes ven lo que queda.
 *
 * <pre>
 * Stock: 10 guantes
 * Pedido 1 (lunes)  pide 8  →  se le asignan 8   (completo)
 * Pedido 2 (martes) pide 6  →  se le asignan 2   (faltan 4)
 * </pre>
 *
 * Así nunca aparecen dos pedidos "completos" contando las mismas unidades.
 * Es un cálculo puro (no toca la base de datos): el stock real solo se descuenta al despachar.
 */
final class RepartoDeStock {

    /** Lo que un ítem de pedido pendiente espera de un producto. */
    record Demanda(Long itemId, Long productoId, int cantidad) {
    }

    private RepartoDeStock() {
    }

    /**
     * @param demandasEnOrden      ítems pendientes, del pedido más antiguo al más nuevo
     * @param disponiblePorProducto stock disponible (no vencido) de cada producto; si falta, es 0
     * @return unidades asignadas a cada ítem (por id de ítem)
     */
    static Map<Long, Integer> repartir(List<Demanda> demandasEnOrden, Map<Long, Integer> disponiblePorProducto) {
        Map<Long, Integer> restante = new HashMap<>(disponiblePorProducto);
        Map<Long, Integer> asignado = new HashMap<>();
        for (Demanda demanda : demandasEnOrden) {
            int queda = restante.getOrDefault(demanda.productoId(), 0);
            int tomar = Math.min(demanda.cantidad(), Math.max(queda, 0));
            asignado.put(demanda.itemId(), tomar);
            restante.put(demanda.productoId(), queda - tomar);
        }
        return asignado;
    }
}
