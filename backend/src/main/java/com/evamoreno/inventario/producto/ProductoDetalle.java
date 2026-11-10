package com.evamoreno.inventario.producto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.evamoreno.inventario.precio.CategoriaPrecioDto.PrecioCategoria;
import com.evamoreno.inventario.stock.Lote;
import com.evamoreno.inventario.stock.LoteResponse;

/**
 * Todos los datos de un producto, con su stock y sus lotes.
 *
 * @param stockDisponible unidades en lotes no vencidos (las que se pueden despachar)
 * @param stockVencido    unidades en lotes vencidos (hay que darlas de baja)
 * @param lotes           lotes con unidades, en orden FEFO (primero el que vence antes)
 * @param costo           costo de compra (dato interno)
 * @param precios         precio de venta en cada categoría (A, B, C...); vacío si no tiene costo
 */
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
        int stockDisponible,
        int stockVencido,
        EstadoStock estadoStock,
        BigDecimal costo,
        List<PrecioCategoria> precios,
        boolean activo,
        List<String> codigosBarras,
        List<LoteResponse> lotes,
        Instant creadoEn,
        Instant actualizadoEn) {

    static ProductoDetalle de(Producto p, List<Lote> lotesConStock, LocalDate hoy, List<PrecioCategoria> precios) {
        List<LoteResponse> lotes = lotesConStock.stream().map(l -> LoteResponse.de(l, hoy)).toList();
        int vencido = lotes.stream().filter(LoteResponse::vencido).mapToInt(LoteResponse::cantidad).sum();
        int disponible = lotes.stream().mapToInt(LoteResponse::cantidad).sum() - vencido;
        return new ProductoDetalle(p.getId(), p.getCodigo(), p.getNombre(), p.getMarca(), p.getPresentacion(),
                p.getUnidadMedida(), p.getCategoria().getId(), p.getCategoria().getNombre(), p.getStockMinimo(),
                disponible, vencido, EstadoStock.de(disponible, p.getStockMinimo()), p.getCosto(), precios, p.isActivo(),
                p.getCodigosBarras().stream().map(ProductoCodigoBarras::getCodigo).toList(),
                lotes, p.getCreadoEn(), p.getActualizadoEn());
    }
}
