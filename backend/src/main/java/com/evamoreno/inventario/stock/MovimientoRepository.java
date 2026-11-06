package com.evamoreno.inventario.stock;

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
}
