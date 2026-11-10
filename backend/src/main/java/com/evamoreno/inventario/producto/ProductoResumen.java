package com.evamoreno.inventario.producto;

import java.math.BigDecimal;

/**
 * Versión corta de un producto, para listas y resultados de búsqueda.
 *
 * @param stockDisponible unidades en lotes no vencidos
 * @param costo           costo de compra (dato interno; null si aún no se registró)
 */
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
        int stockDisponible,
        EstadoStock estadoStock,
        BigDecimal costo,
        boolean activo) {

    public static ProductoResumen de(Producto p, int stockDisponible) {
        return new ProductoResumen(p.getId(), p.getCodigo(), p.getNombre(), p.getMarca(), p.getPresentacion(),
                p.getUnidadMedida(), p.getCategoria().getId(), p.getCategoria().getNombre(), p.getStockMinimo(),
                stockDisponible, EstadoStock.de(stockDisponible, p.getStockMinimo()), p.getCosto(),
                p.isActivo());
    }
}
