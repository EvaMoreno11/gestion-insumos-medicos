package com.evamoreno.inventario.producto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Datos para crear o actualizar un producto. */
public record ProductoRequest(

        @Schema(description = "Opcional. Si se deja vacío se genera uno (ej. JER-00001)", example = "")
        @Size(max = 40, message = "Máximo 40 caracteres")
        @Pattern(regexp = "^\\s*[A-Za-z0-9-]*\\s*$", message = "Solo letras, números y guiones")
        String codigo,

        @Schema(description = "Tipo + medidas, sin la marca", example = "Jeringa descartable 5 ml con aguja 21G x 1 1/2")
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "Máximo 200 caracteres")
        String nombre,

        @Schema(example = "Marca A")
        @NotBlank(message = "La marca es obligatoria")
        @Size(max = 80, message = "Máximo 80 caracteres")
        String marca,

        @Schema(example = "1")
        @NotNull(message = "La categoría es obligatoria")
        Long categoriaId,

        @Schema(description = "Cómo se vende", example = "Caja x 100")
        @NotBlank(message = "La presentación es obligatoria")
        @Size(max = 80, message = "Máximo 80 caracteres")
        String presentacion,

        @Schema(description = "En qué se cuenta el stock: Caja, Paquete, Unidad...", example = "Caja")
        @NotBlank(message = "La unidad de conteo es obligatoria")
        @Size(max = 20, message = "Máximo 20 caracteres")
        String unidadMedida,

        @Schema(description = "Opcional (por defecto 0). Con cuánto stock avisar", example = "5")
        @PositiveOrZero(message = "Debe ser 0 o más")
        Integer stockMinimo,

        @Schema(description = "Opcional. Costo de compra; el precio de venta = costo + porcentaje del cliente",
                example = "4.80")
        @DecimalMin(value = "0", message = "Debe ser 0 o más")
        @Digits(integer = 8, fraction = 4, message = "Máximo 4 decimales")
        BigDecimal costo,

        @Schema(description = "Opcional. Puede tener varios", example = "[\"7750000000017\"]")
        List<
                @NotBlank(message = "El código de barras no puede estar vacío")
                @Size(max = 50, message = "Máximo 50 caracteres")
                @Pattern(regexp = "^\\s*[A-Za-z0-9-]+\\s*$", message = "Solo letras, números y guiones")
                String> codigosBarras) {
}
