package com.evamoreno.inventario.stock;

public enum TipoMovimiento {
    /** Ingreso de unidades (compra, devolución, carga inicial). */
    ENTRADA,
    /** Salida de unidades (entrega a un cliente). */
    SALIDA,
    /** Corrección al contar físicamente (o baja por vencimiento). La cantidad lleva signo. */
    AJUSTE
}
