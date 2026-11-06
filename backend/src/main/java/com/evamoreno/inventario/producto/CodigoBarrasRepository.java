package com.evamoreno.inventario.producto;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoBarrasRepository extends JpaRepository<ProductoCodigoBarras, Long> {

    @EntityGraph(attributePaths = "producto")
    Optional<ProductoCodigoBarras> findByCodigo(String codigo);
}
