package com.evamoreno.inventario.cliente;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClienteRepository extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    @EntityGraph(attributePaths = "locales")
    Optional<Cliente> findWithLocalesById(Long id);

    Optional<Cliente> findByClaveUnica(String claveUnica);

    Optional<Cliente> findByRucDni(String rucDni);
}
