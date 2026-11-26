package com.evamoreno.inventario.stock;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/lotes")
@Tag(name = "Lotes", description = "Corrección de lotes mal registrados")
public class LoteController {

    private final MovimientoService service;

    public LoteController(MovimientoService service) {
        this.service = service;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corregir un lote (número, vencimiento y cantidad)",
            description = "Si la cantidad cambia, queda un ajuste en el historial. Si el número nuevo ya es de "
                    + "otro lote del producto, los dos se unen en uno (con unidades: 409 hasta confirmar con unir).")
    public LoteResponse corregir(@PathVariable Long id, @Valid @RequestBody EdicionLoteRequest request) {
        return service.corregirLote(id, request);
    }

    @PostMapping("/{id}/baja")
    @Operation(summary = "Dar de baja un lote vencido",
            description = "Lo deja en 0 con un ajuste \"Baja por vencido\". Solo lotes ya vencidos y con unidades (409 si no).")
    public ResultadoMovimiento darDeBaja(@PathVariable Long id) {
        return service.darDeBajaVencido(id);
    }
}
