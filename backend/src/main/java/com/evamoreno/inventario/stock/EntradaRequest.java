package com.evamoreno.inventario.stock;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Ingreso de unidades a un lote (una compra). Si el lote no existe, se crea.
 * El costo pasa a ser el costo del producto (el último de compra) y entra al promedio del lote.
 */
public record EntradaRequest(
        @NotNull(message = "El producto es obligatorio")
        Long productoId,

        @Schema(description = "Opcional. Vacío si el producto no tiene lote", example = "L230415")
        @Size(max = 50, message = "Máximo 50 caracteres")
        @Pattern(regexp = "^[\\sA-Za-z0-9./-]*$", message = "Solo letras, números, puntos, barras y guiones")
        String numeroLote,

        @Schema(description = "Opcional. Solo cuentan el mes y el año: se guarda el último día de ese mes",
                example = "2027-03-31")
        LocalDate fechaVencimiento,

        @Schema(example = "12")
        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "Debe ser mayor que 0")
        @Max(value = 1_000_000, message = "Cantidad demasiado grande")
        Integer cantidad,

        @Schema(description = "Costo de compra por unidad (de la boleta)", example = "8.50")
        @NotNull(message = "El costo es obligatorio")
        @DecimalMin(value = "0", message = "Debe ser 0 o más")
        @DecimalMax(value = "1000000", message = "Costo demasiado grande")
        @Digits(integer = 8, fraction = 4, message = "Máximo 4 decimales")
        BigDecimal costo,

        @Schema(example = "Compra a proveedor")
        @Size(max = 300, message = "Máximo 300 caracteres")
        String nota) {
}
