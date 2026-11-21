package com.evamoreno.inventario.proveedor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    Optional<Proveedor> findByNombreComparable(String nombreComparable);

    List<Proveedor> findAllByOrderByNombreAsc();
}
