package com.evamoreno.inventario.stock;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    @EntityGraph(attributePaths = { "lote", "lote.producto" })
    @Query(value = "select m from Movimiento m where m.lote.producto.id = :productoId order by m.fecha desc, m.id desc",
            countQuery = "select count(m) from Movimiento m where m.lote.producto.id = :productoId")
    Page<Movimiento> historialDeProducto(Long productoId, Pageable pageable);

    /** Movimientos con su lote y su producto (para listarlos sin una consulta por fila). */
    @Query("select m from Movimiento m join fetch m.lote l join fetch l.producto where m.id in :ids")
    List<Movimiento> conLoteYProducto(Collection<Long> ids);

    /** Todo el historial de un lote (para pasarlo a otro al unirlos). */
    List<Movimiento> findByLoteId(Long loteId);

    /** Salidas generadas al despachar esos ítems de pedido, con su lote (para la nota de pedido). */
    @EntityGraph(attributePaths = "lote")
    List<Movimiento> findByPedidoItemIdInOrderById(Collection<Long> pedidoItemIds);
}
