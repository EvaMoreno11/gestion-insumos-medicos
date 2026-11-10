package com.evamoreno.inventario.precio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaPrecioRepository extends JpaRepository<CategoriaPrecio, Long> {

    List<CategoriaPrecio> findAllByOrderByPorcentajeAsc();

    Optional<CategoriaPrecio> findByLetraIgnoreCase(String letra);
}
