package com.evamoreno.inventario.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Salida de unidades. Sin loteId se reparte automáticamente por FEFO. */
public record SalidaRequest(
        @NotNull(message = "El producto es obligatorio")
        Long productoId,

        @Schema(example = "6")
        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "Debe ser mayor que 0")
        @Max(value = 1_000_000, message = "Cantidad demasiado grande")
        Integer cantidad,

        @Schema(description = "Opcional. Para sacar de un lote específico en lugar de usar FEFO")
        Long loteId,

        @Schema(example = "Entrega a cliente")
        @Size(max = 300, message = "Máximo 300 caracteres")
        String nota) {
}
