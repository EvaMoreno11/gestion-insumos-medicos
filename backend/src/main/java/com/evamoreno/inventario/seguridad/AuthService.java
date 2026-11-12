package com.evamoreno.inventario.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.common.TextoBusqueda;

/** Reglas del inicio de sesión: intentos fallidos, bloqueo temporal, primera cuenta y cambio de contraseña. */
@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final AuthenticationManager autenticador;
    private final PasswordEncoder codificador;
    private final Clock clock;
    private final String codigoConfiguracion;

    public AuthService(UsuarioRepository usuarios, AuthenticationManager autenticador, PasswordEncoder codificador,
            Clock clock, @Value("${app.seguridad.codigo-configuracion:}") String codigoConfiguracion) {
        this.usuarios = usuarios;
        this.autenticador = autenticador;
        this.codificador = codificador;
        this.clock = clock;
        this.codigoConfiguracion = codigoConfiguracion == null ? "" : codigoConfiguracion.trim();
    }

    /**
     * Verifica usuario y contraseña. Tras {@value Usuario#MAX_INTENTOS} intentos fallidos seguidos, la cuenta
     * queda bloqueada {@link Usuario#BLOQUEO}. El fallo se guarda aunque se lance la excepción (noRollbackFor).
     * El mensaje de error no dice si falló el usuario o la contraseña, para no dar pistas.
     */
    @Transactional(noRollbackFor = { CredencialesInvalidasException.class, CuentaBloqueadaException.class })
    public Authentication autenticar(String nombre, String clave) {
        Instant ahora = clock.instant();
        Usuario usuario = usuarios.findByUsuarioIgnoreCase(nombre.trim()).orElse(null);
        if (usuario != null && usuario.estaBloqueado(ahora)) {
            throw new CuentaBloqueadaException(minutosRestantes(usuario, ahora));
        }
        try {
            Authentication autenticado = autenticador.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(nombre.trim(), clave));
            if (usuario != null) {
                usuario.registrarIngreso(ahora);
            }
            return autenticado;
        } catch (AuthenticationException e) {
            if (usuario != null) {
                usuario.registrarFallo(ahora);
                if (usuario.estaBloqueado(ahora)) {
                    throw new CuentaBloqueadaException(minutosRestantes(usuario, ahora));
                }
            }
            throw new CredencialesInvalidasException("Usuario o contraseña incorrectos");
        }
    }

    @Transactional(readOnly = true)
    public boolean requiereConfiguracion() {
        return usuarios.count() == 0;
    }

    public boolean requiereCodigo() {
        return !codigoConfiguracion.isEmpty();
    }

    /**
     * Crea la primera cuenta. Solo funciona mientras no exista ninguna; en producción, además, exige el código
     * de configuración (variable de entorno APP_CODIGO_CONFIGURACION) para que nadie más pueda adelantarse.
     */
    @Transactional
    public void crearPrimeraCuenta(AuthDto.ConfigurarRequest r) {
        if (usuarios.count() > 0) {
            throw new ConflictoException("La cuenta ya fue creada. Inicia sesión.");
        }
        if (requiereCodigo() && !iguales(codigoConfiguracion, r.codigo())) {
            throw new CredencialesInvalidasException("El código de configuración no es correcto");
        }
        usuarios.save(new Usuario(TextoBusqueda.limpiar(r.usuario()), codificador.encode(r.clave())));
    }

    @Transactional
    public void cambiarClave(String nombre, AuthDto.CambiarClaveRequest r) {
        Usuario usuario = usuarios.findByUsuarioIgnoreCase(nombre)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario"));
        if (!codificador.matches(r.actual(), usuario.getClaveHash())) {
            throw new CredencialesInvalidasException("La contraseña actual no es correcta");
        }
        usuario.cambiarClave(codificador.encode(r.nueva()));
    }

    /** Compara en tiempo constante (no revela cuántos caracteres coinciden). */
    private static boolean iguales(String esperado, String recibido) {
        return recibido != null && MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                recibido.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static long minutosRestantes(Usuario usuario, Instant ahora) {
        // Redondeo hacia arriba: 14 min 10 s se muestra como "15 minutos"
        long segundos = Duration.between(ahora, usuario.getBloqueadoHasta()).toSeconds();
        return Math.max(1, (segundos + 59) / 60);
    }

    /** Usuario o contraseña incorrectos (se responde 401). */
    public static class CredencialesInvalidasException extends RuntimeException {
        public CredencialesInvalidasException(String mensaje) {
            super(mensaje);
        }
    }

    /** Demasiados intentos fallidos (se responde 429). */
    public static class CuentaBloqueadaException extends RuntimeException {
        public CuentaBloqueadaException(long minutos) {
            super("Demasiados intentos fallidos. Espera " + minutos + (minutos == 1 ? " minuto" : " minutos")
                    + " e intenta de nuevo.");
        }
    }
}
