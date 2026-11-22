package com.evamoreno.inventario.compra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecordatorioCompraRepository extends JpaRepository<RecordatorioCompra, Long> {

    Optional<RecordatorioCompra> findByProductoId(Long productoId);

    /** Todos, con su producto (para armar "Por comprar" sin una consulta por fila). */
    @EntityGraph(attributePaths = "producto")
    @Query("select r from RecordatorioCompra r")
    List<RecordatorioCompra> todos();
}
