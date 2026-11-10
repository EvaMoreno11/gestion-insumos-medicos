package com.evamoreno.inventario.precio;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Peticiones y respuestas de las categorías de precio. */
public final class CategoriaPrecioDto {

    private CategoriaPrecioDto() {
    }

    public record Request(
            @Schema(example = "D")
            @NotBlank(message = "La letra es obligatoria")
            @Pattern(regexp = "^\\s*[A-Za-z0-9]{1,10}\\s*$", message = "Solo letras o números (máximo 10)")
            String letra,

            @Schema(description = "Incremento sobre el costo. 25 significa 25 %", example = "25")
            @NotNull(message = "El porcentaje es obligatorio")
            @DecimalMin(value = "0", message = "Debe ser 0 o más")
            @DecimalMax(value = "300", message = "Máximo 300")
            @Digits(integer = 3, fraction = 2, message = "Máximo 2 decimales")
            BigDecimal porcentaje) {
    }

    public record Response(Long id, String letra, BigDecimal porcentaje) {

        static Response de(CategoriaPrecio c) {
            return new Response(c.getId(), c.getLetra(), c.getPorcentaje());
        }
    }

    /** Precio de un producto para una categoría. */
    public record PrecioCategoria(String letra, BigDecimal porcentaje, BigDecimal precio) {
    }
}
