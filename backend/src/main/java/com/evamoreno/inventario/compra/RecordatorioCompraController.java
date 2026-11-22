package com.evamoreno.inventario.compra;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Productos agregados a mano a "Por comprar". La lista completa sale de GET /api/pedidos/por-comprar. */
@RestController
@RequestMapping("/api/pedidos/por-comprar/a-mano")
@Tag(name = "Por comprar", description = "Productos agregados a mano a la lista de compras")
public class RecordatorioCompraController {

    private final RecordatoriosDeCompra service;

    public RecordatorioCompraController(RecordatoriosDeCompra service) {
        this.service = service;
    }

    @PutMapping("/{productoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Agregar un producto a Por comprar (o cambiar su cantidad y nota)",
            description = "La cantidad es opcional. Se quita solo al registrar una entrada de ese producto.")
    public void guardar(@PathVariable Long productoId, @Valid @RequestBody RecordatorioCompraRequest request) {
        service.guardar(productoId, request);
    }

    @DeleteMapping("/{productoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar un producto agregado a mano (lo que falta para pedidos sigue en la lista)")
    public void quitar(@PathVariable Long productoId) {
        service.quitar(productoId);
    }
}
