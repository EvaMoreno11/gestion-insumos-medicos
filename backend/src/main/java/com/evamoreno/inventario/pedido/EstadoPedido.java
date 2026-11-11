package com.evamoreno.inventario.pedido;

public enum EstadoPedido {
    /** En preparación: se puede editar, puede tener faltantes y separa stock por orden de llegada. */
    BORRADOR,
    /** Entregado: se descontaron los lotes y tiene número de nota. Ya no se modifica. */
    DESPACHADO,
    /** Cancelado antes de despachar: libera el stock que tenía separado. */
    ANULADO
}
