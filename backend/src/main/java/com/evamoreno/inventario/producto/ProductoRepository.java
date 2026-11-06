package com.evamoreno.inventario.producto;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    /** Búsqueda con filtros; trae la categoría en la misma consulta para evitar N+1. */
    @Override
    @EntityGraph(attributePaths = "categoria")
    Page<Producto> findAll(Specification<Producto> spec, Pageable pageable);

    @EntityGraph(attributePaths = { "categoria", "codigosBarras" })
    Optional<Producto> findWithDetalleById(Long id);

    Optional<Producto> findByCodigoIgnoreCase(String codigo);

    Optional<Producto> findByClaveUnica(String claveUnica);

    boolean existsByCategoriaId(Long categoriaId);

    @EntityGraph(attributePaths = "categoria")
    List<Producto> findAllByIdIn(Collection<Long> ids);

    /** Productos activos cuyo stock disponible (lotes no vencidos) es menor o igual a su stock mínimo. */
    @Query(value = """
            SELECT p.id
            FROM producto p
            LEFT JOIN lote l ON l.producto_id = p.id
                 AND l.cantidad_actual > 0
                 AND (l.fecha_vencimiento IS NULL OR l.fecha_vencimiento >= :hoy)
            WHERE p.activo
            GROUP BY p.id, p.stock_minimo
            HAVING COALESCE(SUM(l.cantidad_actual), 0) <= p.stock_minimo
            """, nativeQuery = true)
    List<Long> idsConStockBajo(LocalDate hoy);

    @Query(value = "SELECT nextval('producto_codigo_seq')", nativeQuery = true)
    long siguienteNumeroCodigo();
}
