package com.evamoreno.inventario.producto;

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

    @Query(value = "SELECT nextval('producto_codigo_seq')", nativeQuery = true)
    long siguienteNumeroCodigo();
}
