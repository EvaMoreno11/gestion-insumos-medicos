package com.evamoreno.inventario.resumen;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Respuestas del resumen ejecutivo. Los montos incluyen IGV (igual que los costos y los precios).
 * Las ventas son los pedidos despachados en el periodo (con nota o "Solo tabla").
 */
public final class ResumenDto {

    private ResumenDto() {
    }

    // ------------------------------------------------------------------ pantalla principal

    public record Resumen(Periodo periodo, Ventas ventas, Ganancia ganancia, Compras compras,
            List<Mes> ventasPorMes) {
    }

    /** @param pedidos pedidos despachados (cada entrega cuenta, también los saldos) */
    public record Ventas(BigDecimal total, long pedidos, BigDecimal totalAnterior, long pedidosAnterior) {
    }

    /**
     * Ganancia bruta = ventas con costo conocido − lo que costó lo entregado.
     *
     * @param margen         % de ganancia sobre esas ventas (1 decimal); null si no hay ventas con costo
     * @param ventasSinCosto ventas que no entran en la ganancia: ítems escritos a mano o de lotes sin costo
     */
    public record Ganancia(BigDecimal ganancia, BigDecimal margen, BigDecimal ventasConCosto,
            BigDecimal costoVendido, BigDecimal ventasSinCosto, BigDecimal gananciaAnterior,
            BigDecimal margenAnterior) {
    }

    /**
     * Compras = entradas registradas en la app (costo × cantidad). El inventario inicial importado no cuenta.
     *
     * @param entradasSinCosto entradas sin costo (no suman al total)
     */
    public record Compras(BigDecimal total, long entradas, long entradasSinCosto, BigDecimal totalAnterior) {
    }

    /** @param mes "2026-06" */
    public record Mes(String mes, BigDecimal ventas, BigDecimal ganancia) {
    }

    // ------------------------------------------------------------------ más detalles

    public record Detalles(Periodo periodo, List<ProductoVendido> productosMasVendidos,
            List<ClienteTop> mejoresClientes, List<ClienteInactivo> clientesInactivos, int diasInactivo,
            Inventario inventario, List<ProductoSinMovimiento> sinMovimiento, int diasSinMovimiento,
            Pendiente pendiente) {
    }

    public record ProductoVendido(Long productoId, String nombre, String presentacion, String marca, long unidades,
            BigDecimal ventas) {
    }

    public record ClienteTop(Long clienteId, String nombre, long pedidos, BigDecimal ventas) {
    }

    /** Cliente que compraba y no tiene pedidos recientes. */
    public record ClienteInactivo(Long clienteId, String nombre, LocalDate ultimoPedido, long diasSinComprar,
            BigDecimal totalHistorico) {
    }

    /**
     * @param valor             stock vigente × costo promedio de cada lote
     * @param productosSinCosto productos con stock cuyo costo se desconoce (no suman al valor)
     * @param valorVencido      lo que costaron las unidades vencidas que siguen en el inventario
     */
    public record Inventario(BigDecimal valor, long unidades, long productosSinCosto, BigDecimal valorVencido,
            long lotesVencidos) {
    }

    /** Producto con stock que no se vende hace tiempo. @param ultimaSalida null si nunca se vendió */
    public record ProductoSinMovimiento(Long productoId, String nombre, String presentacion, String marca,
            long stock, BigDecimal valor, LocalDate ultimaSalida) {
    }

    /** Pedidos en borrador (incluye los saldos de entregas parciales). */
    public record Pendiente(BigDecimal total, long pedidos, long saldos) {
    }
}
