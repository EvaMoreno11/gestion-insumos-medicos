package com.evamoreno.inventario.pedido;

/** Con qué documento se entrega un pedido. */
public enum DocumentoPedido {
    /** Nota de pedido: nombre del negocio, datos del cliente, fecha y número correlativo. */
    NOTA,
    /** Solo la tabla de productos con el total: sin nombres, sin fecha y sin número. */
    SOLO_TABLA
}
