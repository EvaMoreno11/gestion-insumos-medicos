package com.evamoreno.inventario.negocio;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NegocioRepository extends JpaRepository<Negocio, Short> {
}
