package com.evamoreno.inventario.seguridad;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Peticiones y respuestas del inicio de sesión. */
public final class AuthDto {

    private AuthDto() {
    }

    public record LoginRequest(
            @NotBlank(message = "Escribe el usuario")
            String usuario,

            @NotBlank(message = "Escribe la contraseña")
            String clave,

            @Schema(description = "true: el dispositivo recuerda la sesión por 30 días")
            boolean recordar) {
    }

    /** Creación de la primera cuenta (solo se permite cuando no existe ninguna). */
    public record ConfigurarRequest(
            @NotBlank(message = "Escribe un usuario")
            @Size(min = 3, max = 40, message = "Entre 3 y 40 caracteres")
            @Pattern(regexp = "^\\s*[\\p{L}0-9._-]+\\s*$", message = "Solo letras, números, puntos, guiones y guion bajo")
            String usuario,

            @NotBlank(message = "Escribe una contraseña")
            @Size(min = 8, max = 72, message = "Debe tener al menos 8 caracteres")
            String clave,

            @Schema(description = "Código de configuración, si el servidor lo exige (en producción)")
            String codigo) {
    }

    public record CambiarClaveRequest(
            @NotBlank(message = "Escribe tu contraseña actual")
            String actual,

            @NotBlank(message = "Escribe la nueva contraseña")
            @Size(min = 8, max = 72, message = "Debe tener al menos 8 caracteres")
            String nueva) {
    }

    /**
     * Lo que la app necesita saber al abrirse.
     *
     * @param requiereConfiguracion true si aún no existe ninguna cuenta (hay que crear la primera)
     * @param requiereCodigo        true si para crear la primera cuenta se exige el código de configuración
     * @param negocio               nombre del negocio, para mostrarlo en la pantalla de inicio de sesión
     */
    public record Estado(boolean autenticado, String usuario, boolean requiereConfiguracion, boolean requiereCodigo,
            String negocio) {
    }
}
