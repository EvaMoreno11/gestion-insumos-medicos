package com.evamoreno.inventario.seguridad;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsuarioIgnoreCase(String usuario);
}
