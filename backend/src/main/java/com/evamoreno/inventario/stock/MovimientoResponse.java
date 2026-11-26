package com.evamoreno.inventario.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.evamoreno.inventario.producto.Producto;

/**
 * Un movimiento del historial.
 *
 * @param inicial      true si es de la carga inicial (importación del Excel)
 * @param pedidoId     el pedido del que salió o al que volvió (salidas y devoluciones); null en los demás
 * @param pedidoNumero su N° de nota (null si es "solo tabla")
 * @param cliente      el cliente de ese pedido
 */
public record MovimientoResponse(
        Long id,
        Instant fecha,
        TipoMovimiento tipo,
        int cantidad,
        int stockResultanteLote,
        BigDecimal costoUnitario,
        String nota,
        Long loteId,
        String numeroLote,
        LocalDate fechaVencimiento,
        Long productoId,
        String productoCodigo,
        String productoNombre,
        String productoPresentacion,
        String productoMarca,
        boolean inicial,
        Long pedidoId,
        String pedidoNumero,
        String cliente) {

    static MovimientoResponse de(Movimiento m) {
        Lote lote = m.getLote();
        Producto p = lote.getProducto();
        return new MovimientoResponse(m.getId(), m.getFecha(), m.getTipo(), m.getCantidad(), m.getStockResultante(),
                m.getCostoUnitario(), m.getNota(), lote.getId(), lote.getNumeroLote(), lote.getFechaVencimiento(), p.getId(), p.getCodigo(),
                p.getNombre(), p.getPresentacion(), p.getMarca(), m.isInicial(), null, null, null);
    }

    /** El mismo movimiento con el pedido y el cliente al que corresponde. */
    MovimientoResponse conPedido(Long pedido, String numero, String nombreCliente) {
        return new MovimientoResponse(id, fecha, tipo, cantidad, stockResultanteLote, costoUnitario, nota, loteId,
                numeroLote, fechaVencimiento, productoId, productoCodigo, productoNombre, productoPresentacion,
                productoMarca, inicial, pedido, numero, nombreCliente);
    }
}
