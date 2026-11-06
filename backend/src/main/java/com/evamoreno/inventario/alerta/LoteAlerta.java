package com.evamoreno.inventario.alerta;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.stock.Lote;

/**
 * Un lote que requiere atención (por vencer o vencido), con los datos del producto.
 *
 * @param diasParaVencer negativo si ya venció
 */
public record LoteAlerta(
        Long loteId,
        String numeroLote,
        LocalDate fechaVencimiento,
        long diasParaVencer,
        int cantidad,
        Long productoId,
        String productoCodigo,
        String productoNombre,
        String marca,
        String presentacion) {

    static LoteAlerta de(Lote lote, LocalDate hoy) {
        Producto p = lote.getProducto();
        return new LoteAlerta(lote.getId(), lote.getNumeroLote(), lote.getFechaVencimiento(),
                ChronoUnit.DAYS.between(hoy, lote.getFechaVencimiento()), lote.getCantidadActual(),
                p.getId(), p.getCodigo(), p.getNombre(), p.getMarca(), p.getPresentacion());
    }
}
