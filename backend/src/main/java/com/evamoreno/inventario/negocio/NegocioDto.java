package com.evamoreno.inventario.negocio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NegocioDto(
        @Schema(example = "Distribuidora Demo")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "Máximo 120 caracteres")
        String nombre,

        @Schema(description = "También se usa como punto de partida", example = "Av. Principal 123, Lima")
        @Size(max = 200, message = "Máximo 200 caracteres")
        String direccion,

        @Schema(example = "987 654 321")
        @Size(max = 40, message = "Máximo 40 caracteres")
        String telefono) {

    static NegocioDto de(Negocio n) {
        return new NegocioDto(n.getNombre(), n.getDireccion(), n.getTelefono());
    }
}
