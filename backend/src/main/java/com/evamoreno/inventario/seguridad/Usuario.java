package com.evamoreno.inventario.seguridad;

import java.time.Duration;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una cuenta para entrar a la app. La contraseña solo se guarda como hash BCrypt. */
@Entity
@Table(name = "usuario")
public class Usuario {

    /** Intentos fallidos seguidos antes de bloquear la cuenta un rato. */
    static final int MAX_INTENTOS = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String usuario;

    @Column(name = "clave_hash", nullable = false, length = 100)
    private String claveHash;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "ultimo_ingreso")
    private Instant ultimoIngreso;

    protected Usuario() {
        // requerido por JPA
    }

    Usuario(String usuario, String claveHash) {
        this.usuario = usuario;
        this.claveHash = claveHash;
    }

    boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /** Cuenta un intento fallido; al llegar al máximo, bloquea la cuenta por un rato. */
    void registrarFallo(Instant ahora) {
        intentosFallidos++;
        if (intentosFallidos >= MAX_INTENTOS) {
            bloqueadoHasta = ahora.plus(BLOQUEO);
            intentosFallidos = 0;
        }
    }

    void registrarIngreso(Instant ahora) {
        intentosFallidos = 0;
        bloqueadoHasta = null;
        ultimoIngreso = ahora;
    }

    void cambiarClave(String nuevoHash) {
        this.claveHash = nuevoHash;
    }

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getClaveHash() {
        return claveHash;
    }

    public boolean isActivo() {
        return activo;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }
}
