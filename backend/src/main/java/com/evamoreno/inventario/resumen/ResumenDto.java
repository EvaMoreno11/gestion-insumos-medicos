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

    public record Resumen(Periodo periodo, Ventas ventas, Ganancia ganancia, Cobrado cobrado, Compras compras,
            List<Mes> ventasPorMes) {
    }

    /**
     * @param pedidos pedidos despachados (cada entrega cuenta, también los saldos)
     * @param cobrado de lo vendido en el periodo, cuánto se ha cobrado hasta hoy
     */
    public record Ventas(BigDecimal total, long pedidos, BigDecimal totalAnterior, long pedidosAnterior,
            BigDecimal cobrado) {
    }

    /**
     * Vender no es cobrar: esto es la plata que entró en el periodo (pagos registrados en esas fechas,
     * sean de ventas de este periodo o de anteriores).
     *
     * @param porCobrar todo lo que se debe hoy
     * @param vencido   de eso, lo que ya pasó su plazo
     */
    public record Cobrado(BigDecimal total, long pagos, BigDecimal totalAnterior, BigDecimal porCobrar,
            BigDecimal vencido) {
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

    /** @param mes "2026-06" @param cobrado pagos recibidos en ese mes */
    public record Mes(String mes, BigDecimal ventas, BigDecimal ganancia, BigDecimal cobrado) {
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

    /**
     * Lo que compró un cliente en el periodo y lo que dejó de ganancia.
     *
     * @param ganancia ventas con costo conocido menos su costo real (como la ganancia del Resumen)
     * @param margen   % de ganancia sobre esas ventas, con 1 decimal; null si no hay ventas con costo
     */
    public record ClienteTop(Long clienteId, String nombre, long pedidos, BigDecimal ventas, BigDecimal ganancia,
            BigDecimal margen) {
    }

    /**
     * Compras de un cliente en lo que va del año (para su ficha) y su último pedido despachado.
     *
     * @param ventasSinCosto lo vendido que no entra en la ganancia (ítems a mano o de lotes sin costo)
     * @param ultimoPedido   día de su último despacho (de cualquier año); null si nunca compró
     */
    public record ResumenCliente(LocalDate desde, LocalDate hasta, BigDecimal ventas, long pedidos,
            BigDecimal ganancia, BigDecimal margen, BigDecimal ventasSinCosto, LocalDate ultimoPedido) {
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
    /**
     * @param valorDadoDeBaja costo de lo que se dio de baja por vencido en el periodo (ya no está en el inventario)
     */
    public record Inventario(BigDecimal valor, long unidades, long productosSinCosto, BigDecimal valorVencido,
            long lotesVencidos, BigDecimal valorDadoDeBaja) {
    }

    /** Producto con stock que no se vende hace tiempo. @param ultimaSalida null si nunca se vendió */
    public record ProductoSinMovimiento(Long productoId, String nombre, String presentacion, String marca,
            long stock, BigDecimal valor, LocalDate ultimaSalida) {
    }

    /** Pedidos en borrador (incluye los saldos de entregas parciales). */
    public record Pendiente(BigDecimal total, long pedidos, long saldos) {
    }
}
