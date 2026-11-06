package com.evamoreno.inventario.alerta;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.evamoreno.inventario.producto.ProductoResumen;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/alertas")
@Tag(name = "Alertas", description = "Lo que necesita atención: stock bajo, por vencer y vencido")
public class AlertaController {

    private final AlertaService service;

    public AlertaController(AlertaService service) {
        this.service = service;
    }

    @GetMapping("/stock-bajo")
    @Operation(summary = "Productos con stock igual o menor al mínimo (primero los agotados)")
    public List<ProductoResumen> stockBajo() {
        return service.stockBajo();
    }

    @GetMapping("/por-vencer")
    @Operation(summary = "Lotes que vencen pronto")
    public List<LoteAlerta> porVencer(
            @Parameter(description = "Ventana en días desde hoy (1 a 365)")
            @RequestParam(defaultValue = "60") @Min(1) @Max(365) int dias) {
        return service.porVencer(dias);
    }

    @GetMapping("/vencidos")
    @Operation(summary = "Lotes vencidos que aún tienen unidades")
    public List<LoteAlerta> vencidos() {
        return service.vencidos();
    }
}
