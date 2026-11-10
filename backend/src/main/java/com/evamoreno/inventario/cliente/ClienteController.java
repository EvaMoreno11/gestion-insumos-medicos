package com.evamoreno.inventario.cliente;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.evamoreno.inventario.cliente.ClienteDto.Detalle;
import com.evamoreno.inventario.cliente.ClienteDto.Request;
import com.evamoreno.inventario.cliente.ClienteDto.Resumen;
import com.evamoreno.inventario.common.Pagina;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes", description = "Clientes, sus locales de entrega y su porcentaje de precio")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Buscar clientes por nombre o RUC/DNI")
    public Pagina<Resumen> buscar(
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @Parameter(description = "Número de página, desde 0") @RequestParam(defaultValue = "0") int pagina,
            @Parameter(description = "Resultados por página (máx. 100)") @RequestParam(defaultValue = "20") int tamano) {
        return service.buscar(buscar, incluirInactivos, pagina, tamano);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un cliente con sus locales")
    public Detalle obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Registrar un cliente")
    public ResponseEntity<Detalle> crear(@Valid @RequestBody Request request) {
        Detalle creado = service.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un cliente (datos, porcentaje y locales)")
    public Detalle actualizar(@PathVariable Long id, @Valid @RequestBody Request request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/desactivar")
    @Operation(summary = "Desactivar un cliente (deja de aparecer en búsquedas)")
    public Detalle desactivar(@PathVariable Long id) {
        return service.cambiarEstado(id, false);
    }

    @PatchMapping("/{id}/activar")
    @Operation(summary = "Volver a activar un cliente")
    public Detalle activar(@PathVariable Long id) {
        return service.cambiarEstado(id, true);
    }
}
