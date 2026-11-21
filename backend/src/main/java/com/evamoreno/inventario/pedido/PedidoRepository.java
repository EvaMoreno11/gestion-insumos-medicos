package com.evamoreno.inventario.pedido;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = { "cliente", "local", "items", "items.producto" })
    Optional<Pedido> findConTodoById(Long id);

    /** Pedidos con su cliente, local e ítems (para listar los despachados de una página). */
    @EntityGraph(attributePaths = { "cliente", "local", "items" })
    List<Pedido> findByIdIn(Collection<Long> ids);

    /** Saldos de un pedido (lo que faltó entregar), del más antiguo al más nuevo. */
    @EntityGraph(attributePaths = "items")
    List<Pedido> findByOrigenIdOrderById(Long origenId);

    @EntityGraph(attributePaths = { "cliente", "local" })
    @Query("""
            select p from Pedido p
            where (:estado is null or p.estado = :estado)
              and (:clienteId is null or p.cliente.id = :clienteId)
            order by p.id desc
            """)
    Page<Pedido> buscar(EstadoPedido estado, Long clienteId, Pageable pageable);

    /** Resultado de sumar lo pedido de un producto. */
    interface PedidoDeProducto {
        Long getProductoId();

        Long getCantidad();
    }

    /** Cuánto se pide de cada producto en los pedidos pendientes. */
    @Query("""
            select i.producto.id as productoId, sum(i.cantidad) as cantidad
            from PedidoItem i
            where i.pedido.estado = com.evamoreno.inventario.pedido.EstadoPedido.BORRADOR
              and i.producto.id in :productoIds
            group by i.producto.id
            """)
    List<PedidoDeProducto> pedidoEnBorradores(Collection<Long> productoIds);

    /**
     * Ítems de pedidos pendientes para esos productos, del pedido más antiguo al más nuevo:
     * en ese orden se reparte el stock (quien pidió primero tiene prioridad). Un saldo
     * conserva la antigüedad de su pedido original.
     */
    @Query("""
            select i from PedidoItem i join fetch i.pedido p join fetch p.cliente join fetch p.local
            where p.estado = com.evamoreno.inventario.pedido.EstadoPedido.BORRADOR
              and i.producto.id in :productoIds
            order by coalesce(p.origenId, p.id), p.id, i.id
            """)
    List<PedidoItem> itemsPendientesDe(Collection<Long> productoIds);

    /** Todos los ítems de pedidos pendientes (para la lista "Por comprar"). */
    @Query("""
            select i from PedidoItem i join fetch i.pedido p join fetch p.cliente join fetch p.local
            left join fetch i.producto
            where p.estado = com.evamoreno.inventario.pedido.EstadoPedido.BORRADOR
            order by coalesce(p.origenId, p.id), p.id, i.id
            """)
    List<PedidoItem> itemsPendientes();

    /** Bloquea la fila de numeración hasta terminar la transacción: no se repiten ni se saltan números. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NumeracionNota n where n.id = 1")
    NumeracionNota numeracionBloqueada();

    @Query("select n from NumeracionNota n where n.id = 1")
    NumeracionNota numeracion();
}
