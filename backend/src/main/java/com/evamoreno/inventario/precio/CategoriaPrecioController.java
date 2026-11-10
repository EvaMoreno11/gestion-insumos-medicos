package com.evamoreno.inventario.precio;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.evamoreno.inventario.precio.CategoriaPrecioDto.Request;
import com.evamoreno.inventario.precio.CategoriaPrecioDto.Response;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/categorias-precio")
@Tag(name = "Categorías de precio",
        description = "Etiquetas internas (A, B, C...) para el porcentaje que se suma al costo. No se muestran al cliente.")
public class CategoriaPrecioController {

    private final CategoriaPrecioService service;

    public CategoriaPrecioController(CategoriaPrecioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar categorías de precio (de menor a mayor porcentaje)")
    public List<Response> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una categoría de precio")
    public Response crear(@Valid @RequestBody Request request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cambiar la letra o el porcentaje de una categoría")
    public Response actualizar(@PathVariable Long id, @Valid @RequestBody Request request) {
        return service.actualizar(id, request);
    }
}
