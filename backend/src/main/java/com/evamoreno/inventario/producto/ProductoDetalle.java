package com.evamoreno.inventario.producto;

import java.time.Instant;
import java.util.List;

/** Todos los datos de un producto. En la Fase 2 se agregarán el stock y sus lotes. */
public record ProductoDetalle(
        Long id,
        String codigo,
        String nombre,
        String marca,
        String presentacion,
        String unidadMedida,
        Long categoriaId,
        String categoria,
        int stockMinimo,
        boolean activo,
        List<String> codigosBarras,
        Instant creadoEn,
        Instant actualizadoEn) {

    static ProductoDetalle de(Producto p) {
        return new ProductoDetalle(p.getId(), p.getCodigo(), p.getNombre(), p.getMarca(), p.getPresentacion(),
                p.getUnidadMedida(), p.getCategoria().getId(), p.getCategoria().getNombre(), p.getStockMinimo(),
                p.isActivo(), p.getCodigosBarras().stream().map(ProductoCodigoBarras::getCodigo).toList(),
                p.getCreadoEn(), p.getActualizadoEn());
    }
}
