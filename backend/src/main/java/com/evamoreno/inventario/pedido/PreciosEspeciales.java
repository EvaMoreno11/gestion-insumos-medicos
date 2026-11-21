package com.evamoreno.inventario.pedido;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Precios especiales de cada cliente: el último precio escrito a mano para un producto en un pedido suyo (no
 * anulado). Sus pedidos nuevos, sus correcciones y su lista de precios usan ese precio en vez de costo + porcentaje.
 */
@Component
public class PreciosEspeciales {

    private final PedidoRepository pedidos;

    public PreciosEspeciales(PedidoRepository pedidos) {
        this.pedidos = pedidos;
    }

    /** El precio especial de ese producto para el cliente, sin contar el pedido indicado; null si no tiene. */
    @Transactional(readOnly = true)
    public BigDecimal de(Long clienteId, Long productoId, Long sinContarPedidoId) {
        return pedidos.preciosManualesDelCliente(clienteId, productoId,
                sinContarPedidoId == null ? -1L : sinContarPedidoId, EstadoPedido.ANULADO, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
    }

    /** Todos los precios especiales del cliente, por producto (para su lista de precios). */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> delCliente(Long clienteId) {
        Map<Long, BigDecimal> precios = new HashMap<>();
        List<PedidoRepository.PrecioDeProducto> filas = pedidos.preciosManualesDelCliente(clienteId,
                EstadoPedido.ANULADO);
        for (PedidoRepository.PrecioDeProducto fila : filas) {
            precios.putIfAbsent(fila.getProductoId(), fila.getPrecio()); // vienen del más reciente al más antiguo
        }
        return precios;
    }
}
