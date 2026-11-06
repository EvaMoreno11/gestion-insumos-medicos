package com.evamoreno.inventario.stock;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Ingreso de unidades a un lote. Si el lote no existe, se crea. */
public record EntradaRequest(
        @NotNull(message = "El producto es obligatorio")
        Long productoId,

        @Schema(description = "Opcional. Vacío si el producto no tiene lote", example = "L230415")
        @Size(max = 50, message = "Máximo 50 caracteres")
        @Pattern(regexp = "^[\\sA-Za-z0-9./-]*$", message = "Solo letras, números, puntos, barras y guiones")
        String numeroLote,

        @Schema(description = "Opcional. Formato aaaa-mm-dd", example = "2027-03-15")
        LocalDate fechaVencimiento,

        @Schema(example = "12")
        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "Debe ser mayor que 0")
        @Max(value = 1_000_000, message = "Cantidad demasiado grande")
        Integer cantidad,

        @Schema(example = "Compra a proveedor")
        @Size(max = 300, message = "Máximo 300 caracteres")
        String nota) {
}
