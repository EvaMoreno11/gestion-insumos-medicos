package com.evamoreno.inventario.compra;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Agregar (o cambiar) un producto en "Por comprar". Las dos cosas son opcionales. */
public record RecordatorioCompraRequest(
        @Schema(description = "Cuántos comprar; vacío si todavía no se sabe", example = "20")
        @Positive(message = "Debe ser mayor que 0")
        @Max(value = 1_000_000, message = "Cantidad demasiado grande")
        Integer cantidad,

        @Schema(example = "Urgente, se acaba esta semana")
        @Size(max = 300, message = "Máximo 300 caracteres")
        String nota) {
}
