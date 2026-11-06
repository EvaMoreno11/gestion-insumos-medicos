package com.evamoreno.inventario.stock;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    /** Resultado de sumar el stock disponible de un producto. */
    interface StockProducto {
        Long getProductoId();

        Long getDisponible();
    }

    /** Stock disponible (lotes NO vencidos) de varios productos en una sola consulta. */
    @Query("""
            select l.producto.id as productoId, sum(l.cantidadActual) as disponible
            from Lote l
            where l.producto.id in :productoIds
              and (l.fechaVencimiento is null or l.fechaVencimiento >= :hoy)
            group by l.producto.id
            """)
    List<StockProducto> stockDisponible(Collection<Long> productoIds, LocalDate hoy);

    /** Lotes con unidades de un producto, en orden FEFO (primero el que vence antes). */
    @Query("""
            select l from Lote l
            where l.producto.id = :productoId and l.cantidadActual > 0
            order by l.fechaVencimiento asc nulls last, l.fechaIngreso, l.id
            """)
    List<Lote> conStock(Long productoId);

    /**
     * Lotes que se pueden despachar, en orden FEFO, BLOQUEADOS hasta que termine la transacción
     * (SELECT ... FOR UPDATE). Así dos salidas simultáneas no pueden usar las mismas unidades.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from Lote l
            where l.producto.id = :productoId and l.cantidadActual > 0
              and (l.fechaVencimiento is null or l.fechaVencimiento >= :hoy)
            order by l.fechaVencimiento asc nulls last, l.fechaIngreso, l.id
            """)
    List<Lote> disponiblesParaSalidaBloqueados(Long productoId, LocalDate hoy);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lote l where l.id = :id")
    Optional<Lote> buscarBloqueado(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from Lote l
            where l.producto.id = :productoId
              and (l.numeroLote = :numeroLote or (:numeroLote is null and l.numeroLote is null))
            """)
    Optional<Lote> buscarPorNumeroBloqueado(Long productoId, String numeroLote);

    @EntityGraph(attributePaths = "producto")
    @Query("""
            select l from Lote l
            where l.cantidadActual > 0 and l.producto.activo = true
              and l.fechaVencimiento between :desde and :hasta
            order by l.fechaVencimiento, l.id
            """)
    List<Lote> porVencer(LocalDate desde, LocalDate hasta);

    @EntityGraph(attributePaths = "producto")
    @Query("""
            select l from Lote l
            where l.cantidadActual > 0 and l.producto.activo = true
              and l.fechaVencimiento < :hoy
            order by l.fechaVencimiento, l.id
            """)
    List<Lote> vencidos(LocalDate hoy);
}
