package com.evamoreno.inventario.producto;

/** Versión corta de un producto, para listas y resultados de búsqueda. */
public record ProductoResumen(
        Long id,
        String codigo,
        String nombre,
        String marca,
        String presentacion,
        String unidadMedida,
        Long categoriaId,
        String categoria,
        int stockMinimo,
        boolean activo) {

    static ProductoResumen de(Producto p) {
        return new ProductoResumen(p.getId(), p.getCodigo(), p.getNombre(), p.getMarca(), p.getPresentacion(),
                p.getUnidadMedida(), p.getCategoria().getId(), p.getCategoria().getNombre(), p.getStockMinimo(),
                p.isActivo());
    }
}
