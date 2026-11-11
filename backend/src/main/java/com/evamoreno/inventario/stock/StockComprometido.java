package com.evamoreno.inventario.stock;

import java.util.Collection;
import java.util.Map;

/**
 * Unidades que los pedidos pendientes (borradores) están esperando de cada producto.
 *
 * <p>Es una interfaz definida aquí e implementada en el paquete de pedidos: así el inventario puede
 * mostrar "hay 10 · separadas 8 · libres 2" sin depender del código de pedidos
 * (inversión de dependencias).</p>
 */
public interface StockComprometido {

    /** Cantidad pedida en borradores por producto. Los productos sin pedidos pendientes no aparecen. */
    Map<Long, Integer> pedidoEnBorradores(Collection<Long> productoIds);

    /** De lo disponible, cuánto queda separado para pedidos pendientes (nunca más de lo que hay). */
    static int separado(int disponible, int pedido) {
        return Math.min(Math.max(disponible, 0), Math.max(pedido, 0));
    }
}
