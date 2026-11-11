package com.evamoreno.inventario.pedido;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.stock.StockComprometido;

/** Informa al inventario cuánto esperan los pedidos pendientes de cada producto. */
@Component
class DemandaDePedidos implements StockComprometido {

    private final PedidoRepository pedidos;

    DemandaDePedidos(PedidoRepository pedidos) {
        this.pedidos = pedidos;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> pedidoEnBorradores(Collection<Long> productoIds) {
        if (productoIds.isEmpty()) {
            return Map.of();
        }
        return pedidos.pedidoEnBorradores(productoIds).stream()
                .collect(Collectors.toMap(PedidoRepository.PedidoDeProducto::getProductoId,
                        p -> p.getCantidad().intValue()));
    }
}
