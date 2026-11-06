package com.evamoreno.inventario.producto;

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

import com.evamoreno.inventario.common.Pagina;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/productos")
@Tag(name = "Productos", description = "Catálogo de productos y buscador")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Buscar productos",
            description = "Busca sin importar mayúsculas ni tildes; cada palabra debe coincidir con el inicio "
                    + "de una palabra del producto. Ej: \"jer 5ml\" encuentra \"Jeringa 5 ml\".")
    public Pagina<ProductoResumen> buscar(
            @Parameter(description = "Texto a buscar (nombre, marca, presentación o código)")
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @Parameter(description = "Número de página, desde 0") @RequestParam(defaultValue = "0") int pagina,
            @Parameter(description = "Resultados por página (máx. 100)") @RequestParam(defaultValue = "20") int tamano) {
        return service.buscar(buscar, categoriaId, incluirInactivos, pagina, tamano);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver el detalle de un producto")
    public ProductoDetalle obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping("/codigo-barras/{codigo}")
    @Operation(summary = "Buscar un producto por su código de barras")
    public ProductoDetalle obtenerPorCodigoBarras(@PathVariable String codigo) {
        return service.obtenerPorCodigoBarras(codigo);
    }

    @PostMapping
    @Operation(summary = "Registrar un producto")
    public ResponseEntity<ProductoDetalle> crear(@Valid @RequestBody ProductoRequest request) {
        ProductoDetalle creado = service.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un producto", description = "Si el código se envía vacío, se conserva el actual.")
    public ProductoDetalle actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/desactivar")
    @Operation(summary = "Desactivar un producto (deja de aparecer en búsquedas)")
    public ProductoDetalle desactivar(@PathVariable Long id) {
        return service.cambiarEstado(id, false);
    }

    @PatchMapping("/{id}/activar")
    @Operation(summary = "Volver a activar un producto")
    public ProductoDetalle activar(@PathVariable Long id) {
        return service.cambiarEstado(id, true);
    }
}
