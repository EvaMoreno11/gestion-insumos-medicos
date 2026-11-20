package com.evamoreno.inventario.stock;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Corrección de un lote mal registrado (en la app o en el Excel importado): número, vencimiento y cantidad.
 * El costo no se edita aquí.
 *
 * @param unir confirma que, si ya hay otro lote de este producto con ese número, se unan en uno solo
 *             (si no viene, es false)
 */
public record EdicionLoteRequest(
        @Schema(description = "Vacío si el producto no tiene lote", example = "L230415")
        @Size(max = 50, message = "Máximo 50 caracteres")
        @Pattern(regexp = "^[\\sA-Za-z0-9./-]*$", message = "Solo letras, números, puntos, barras y guiones")
        String numeroLote,

        @Schema(description = "Vacío si no vence. Solo cuentan el mes y el año", example = "2027-03-31")
        LocalDate fechaVencimiento,

        @Schema(description = "Unidades que realmente hay en el lote", example = "12")
        @NotNull(message = "La cantidad es obligatoria")
        @PositiveOrZero(message = "Debe ser 0 o más")
        @Max(value = 1_000_000, message = "Cantidad demasiado grande")
        Integer cantidad,

        @Schema(description = "Motivo de la corrección (opcional)", example = "Error en el Excel")
        @Size(max = 300, message = "Máximo 300 caracteres")
        String nota,

        Boolean unir) {
}
