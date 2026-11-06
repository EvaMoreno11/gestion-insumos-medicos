package com.evamoreno.inventario.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Corrige la cantidad de un lote a lo que realmente hay (conteo físico o baja por vencimiento). */
public record AjusteRequest(
        @NotNull(message = "El lote es obligatorio")
        Long loteId,

        @Schema(description = "Cantidad que realmente hay en el lote", example = "10")
        @NotNull(message = "La cantidad real es obligatoria")
        @PositiveOrZero(message = "Debe ser 0 o más")
        @Max(value = 1_000_000, message = "Cantidad demasiado grande")
        Integer cantidadReal,

        @Schema(example = "Conteo físico: faltaban 2 cajas")
        @NotBlank(message = "Indica el motivo del ajuste")
        @Size(max = 300, message = "Máximo 300 caracteres")
        String nota) {
}
