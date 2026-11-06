package com.evamoreno.inventario.stock;

import java.util.List;

/**
 * Resultado de una entrada, salida o ajuste.
 *
 * @param movimientos       uno por cada lote afectado (una salida FEFO puede tocar varios lotes)
 * @param stockDisponible   stock disponible del producto después de la operación
 */
public record ResultadoMovimiento(Long productoId, List<MovimientoResponse> movimientos, int stockDisponible) {
}
