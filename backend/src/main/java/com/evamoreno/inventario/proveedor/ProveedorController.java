package com.evamoreno.inventario.proveedor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.pedido.PedidoDto.PagoRequest;
import com.evamoreno.inventario.proveedor.ProveedorDto.DeudaRequest;
import com.evamoreno.inventario.proveedor.ProveedorDto.DeudaResponse;
import com.evamoreno.inventario.proveedor.ProveedorDto.ProveedorResponse;
import com.evamoreno.inventario.proveedor.ProveedorDto.Resumen;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/proveedores")
@Tag(name = "Proveedores", description = "Deudas con proveedores y sus pagos (solo un control; no toca el Resumen)")
public class ProveedorController {

    private final ProveedorService service;

    public ProveedorController(ProveedorService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Proveedores, en orden alfabético (para elegir al anotar una deuda)")
    public List<ProveedorResponse> proveedores() {
        return service.proveedores();
    }

    @GetMapping("/resumen")
    @Operation(summary = "Cuánto falta pagar en total y cuántas deudas están por pagar")
    public Resumen resumen() {
        return service.resumen();
    }

    @GetMapping("/deudas")
    @Operation(summary = "Deudas por pagar (primero la más antigua) o pagadas (primero la última que se terminó)")
    public Pagina<DeudaResponse> deudas(@RequestParam(defaultValue = "false") boolean pagadas,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        return service.deudas(pagadas, pagina, tamano);
    }

    @PostMapping("/deudas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Anotar una deuda", description = "Si el proveedor no existe, se crea con ese nombre.")
    public DeudaResponse crear(@Valid @RequestBody DeudaRequest request) {
        return service.crear(request);
    }

    @PutMapping("/deudas/{id}")
    @Operation(summary = "Corregir una deuda (el monto no puede ser menor que lo ya pagado)")
    public DeudaResponse corregir(@PathVariable Long id, @Valid @RequestBody DeudaRequest request) {
        return service.corregir(id, request);
    }

    @DeleteMapping("/deudas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar una deuda anotada por error (solo si no tiene pagos)")
    public void eliminar(@PathVariable Long id) {
        service.eliminar(id);
    }

    @PostMapping("/deudas/{id}/pagos")
    @Operation(summary = "Registrar un pago (total o parcial)")
    public DeudaResponse registrarPago(@PathVariable Long id, @Valid @RequestBody PagoRequest request) {
        return service.registrarPago(id, request);
    }

    @DeleteMapping("/deudas/{id}/pagos/{pagoId}")
    @Operation(summary = "Borrar un pago mal anotado")
    public DeudaResponse eliminarPago(@PathVariable Long id, @PathVariable Long pagoId) {
        return service.eliminarPago(id, pagoId);
    }
}
