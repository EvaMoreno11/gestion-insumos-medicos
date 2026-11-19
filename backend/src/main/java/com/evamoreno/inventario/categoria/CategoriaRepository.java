package com.evamoreno.inventario.categoria;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    /** Todas las categorías con cuántos productos tienen (también las vacías), en orden alfabético. */
    @Query("""
            SELECT c.id AS id, c.nombre AS nombre, COUNT(p.id) AS productos
            FROM Categoria c LEFT JOIN Producto p ON p.categoria = c
            GROUP BY c.id, c.nombre
            ORDER BY c.nombre""")
    List<CategoriaConProductos> listarConProductos();

    Optional<Categoria> findByNombreIgnoreCase(String nombre);

    /** Proyección de {@link #listarConProductos()}. */
    interface CategoriaConProductos {
        Long getId();

        String getNombre();

        Long getProductos();
    }
}
