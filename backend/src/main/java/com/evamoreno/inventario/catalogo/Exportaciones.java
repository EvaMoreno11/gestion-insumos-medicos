package com.evamoreno.inventario.catalogo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contenido de los archivos que se exportan, listo para dibujarse en Excel o PDF. */
public final class Exportaciones {

    private Exportaciones() {
    }

    // ------------------------------------------------------------------ inventario (uso interno)

    /**
     * Inventario completo: todos los productos activos, tengan o no stock, y sus lotes con unidades.
     * Incluye costos: es solo para uso interno.
     */
    public record Inventario(LocalDate fecha, List<Producto> productos, List<Lote> lotes) {
    }

    /**
     * @param stockDisponible unidades en lotes no vencidos
     * @param stockMinimo     0 = sin mínimo (no avisa)
     * @param valor           unidades disponibles × costo (el de cada lote o, si no lo tiene, el del producto)
     */
    public record Producto(String codigo, String categoria, String nombre, String marca, String presentacion,
            String unidad, int stockDisponible, int stockMinimo, BigDecimal costo, BigDecimal valor) {
    }

    /** @param vencimiento mes y año (03/2027) o null */
    public record Lote(String codigo, String producto, String marca, String presentacion, String numeroLote,
            String vencimiento, int cantidad, BigDecimal costoPromedio, boolean vencido) {
    }

    // ------------------------------------------------------------------ catálogo (para clientes)

    /**
     * Catálogo para mandar a los clientes: productos activos con stock mínimo, agrupados por categoría.
     * Nunca lleva costos, porcentajes ni cantidades.
     *
     * @param cliente null = catálogo sin precios; si no, lista de precios para ese cliente
     */
    public record Catalogo(String negocio, String negocioContacto, String cliente, LocalDate fecha,
            List<Grupo> grupos, String nombreArchivo) {

        public boolean conPrecios() {
            return cliente != null;
        }
    }

    public record Grupo(String categoria, List<Item> items) {
    }

    /** @param precio null si el producto aún no tiene costo (se muestra "Consultar") */
    public record Item(String nombre, String presentacion, String marca, BigDecimal precio) {
    }
}
