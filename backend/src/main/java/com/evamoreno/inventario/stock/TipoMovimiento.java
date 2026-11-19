package com.evamoreno.inventario.stock;

public enum TipoMovimiento {
    /** Ingreso de unidades (compra, devolución, carga inicial). */
    ENTRADA,
    /** Salida de unidades (entrega a un cliente). */
    SALIDA,
    /** Corrección al contar físicamente (o baja por vencimiento). La cantidad lleva signo. */
    AJUSTE,
    /** Unidades de un pedido despachado que el cliente devolvió: vuelven al lote del que salieron. */
    DEVOLUCION
}
