package com.evamoreno.inventario.proveedor;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeudaProveedorRepository extends JpaRepository<DeudaProveedor, Long> {

    /** Todas las deudas con su proveedor y sus pagos (son pocas: unas cuantas compras grandes al mes). */
    @EntityGraph(attributePaths = { "proveedor", "pagos" })
    @Query("select d from DeudaProveedor d")
    List<DeudaProveedor> todas();
}
