package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.pedido.PedidoDto.CostoPedido;

/**
 * Cuánto costó cada pedido y cuánto se gana con él. Solo para uso interno: nunca va en la nota.
 *
 * <ul>
 * <li>Despachado: lo que costó de verdad lo entregado, con el costo de cada lote al salir y menos lo devuelto
 * (lo mismo que usa la ganancia del Resumen).</li>
 * <li>Borrador: un estimado con el costo actual de cada producto.</li>
 * </ul>
 * Un ítem sin costo conocido (escrito a mano, sin precio o de un lote sin costo) no entra en la cuenta y se informa
 * aparte, para que la ganancia no salga inflada.
 */
@Component
public class CostosDePedidos {

    private final JdbcClient jdbc;

    public CostosDePedidos(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<Long, CostoPedido> despachados(Collection<Long> pedidoIds) {
        Map<Long, CostoPedido> costos = new HashMap<>();
        if (pedidoIds.isEmpty()) {
            return costos;
        }
        jdbc.sql("""
                SELECT x.pedido_id,
                       COALESCE(SUM(x.costo) FILTER (WHERE NOT x.sin_costo), 0) AS costo,
                       COALESCE(SUM(x.venta) FILTER (WHERE NOT x.sin_costo), 0) AS venta_con_costo,
                       COUNT(*) FILTER (WHERE x.sin_costo) AS sin_costo
                FROM (
                    SELECT i.pedido_id,
                           COALESCE(i.cantidad * i.precio_unitario, 0) AS venta,
                           COALESCE(SUM(CASE WHEN m.tipo = 'DEVOLUCION' THEN -1 ELSE 1 END
                                        * m.cantidad * m.costo_unitario), 0) AS costo,
                           (i.producto_id IS NULL OR i.precio_unitario IS NULL
                            OR COALESCE(bool_or(m.id IS NOT NULL AND m.costo_unitario IS NULL), FALSE)) AS sin_costo
                    FROM pedido_item i
                    LEFT JOIN movimiento m ON m.pedido_item_id = i.id AND m.tipo IN ('SALIDA', 'DEVOLUCION')
                    WHERE i.pedido_id IN (:ids) AND i.cantidad > 0
                    GROUP BY i.id
                ) x
                GROUP BY x.pedido_id
                """)
                .param("ids", pedidoIds)
                .query(rs -> {
                    costos.put(rs.getLong("pedido_id"), costo(rs.getBigDecimal("costo"),
                            rs.getBigDecimal("venta_con_costo"), rs.getInt("sin_costo")));
                });
        pedidoIds.forEach(id -> costos.putIfAbsent(id, costo(BigDecimal.ZERO, BigDecimal.ZERO, 0)));
        return costos;
    }

    /** Borrador: cantidad × costo actual de cada producto. */
    static CostoPedido estimado(Pedido pedido) {
        BigDecimal costo = BigDecimal.ZERO;
        BigDecimal ventaConCosto = BigDecimal.ZERO;
        int sinCosto = 0;
        for (PedidoItem item : pedido.getItems()) {
            if (item.getCantidad() == 0) {
                continue;
            }
            BigDecimal costoUnitario = item.esManual() ? null : item.getProducto().getCosto();
            BigDecimal venta = item.total();
            if (costoUnitario == null || venta == null) {
                sinCosto++;
                continue;
            }
            costo = costo.add(costoUnitario.multiply(BigDecimal.valueOf(item.getCantidad())));
            ventaConCosto = ventaConCosto.add(venta);
        }
        return costo(costo, ventaConCosto, sinCosto);
    }

    /**
     * Ganancia = lo vendido con costo conocido − su costo; margen = esa ganancia sobre lo vendido, con 1 decimal
     * (la misma cuenta que el Resumen). Los ítems sin costo no entran en ninguna de las dos.
     */
    private static CostoPedido costo(BigDecimal costo, BigDecimal ventaConCosto, int sinCosto) {
        BigDecimal ganancia = ventaConCosto.subtract(costo);
        BigDecimal margen = ventaConCosto.signum() == 0 ? null
                : ganancia.multiply(BigDecimal.valueOf(100)).divide(ventaConCosto, 1, RoundingMode.HALF_UP);
        return new CostoPedido(dinero(costo), dinero(ganancia), margen, sinCosto);
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
