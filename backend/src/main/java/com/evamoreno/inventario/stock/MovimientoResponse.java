package com.evamoreno.inventario.stock;

import java.time.Instant;
import java.time.LocalDate;

import com.evamoreno.inventario.producto.Producto;

/** Un movimiento del historial. */
public record MovimientoResponse(
        Long id,
        Instant fecha,
        TipoMovimiento tipo,
        int cantidad,
        int stockResultanteLote,
        String nota,
        Long loteId,
        String numeroLote,
        LocalDate fechaVencimiento,
        Long productoId,
        String productoCodigo,
        String productoNombre) {

    static MovimientoResponse de(Movimiento m) {
        Lote lote = m.getLote();
        Producto p = lote.getProducto();
        return new MovimientoResponse(m.getId(), m.getFecha(), m.getTipo(), m.getCantidad(), m.getStockResultante(),
                m.getNota(), lote.getId(), lote.getNumeroLote(), lote.getFechaVencimiento(), p.getId(), p.getCodigo(),
                p.getNombre());
    }
}
