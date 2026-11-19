package com.evamoreno.inventario.categoria;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.producto.ProductoRepository;

@Service
@Transactional
public class CategoriaService {

    private final CategoriaRepository categorias;
    private final ProductoRepository productos;

    public CategoriaService(CategoriaRepository categorias, ProductoRepository productos) {
        this.categorias = categorias;
        this.productos = productos;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar() {
        return categorias.listarConProductos().stream().map(CategoriaResponse::de).toList();
    }

    /** Busca la entidad; la usan otros módulos (p. ej. productos). */
    @Transactional(readOnly = true)
    public Categoria buscarEntidad(Long id) {
        return categorias.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la categoría con id " + id));
    }

    public CategoriaResponse crear(CategoriaRequest request) {
        String nombre = TextoBusqueda.limpiar(request.nombre());
        validarNombreUnico(nombre, null);
        return CategoriaResponse.de(categorias.save(new Categoria(nombre)), 0);
    }

    public CategoriaResponse renombrar(Long id, CategoriaRequest request) {
        Categoria categoria = buscarEntidad(id);
        String nombre = TextoBusqueda.limpiar(request.nombre());
        validarNombreUnico(nombre, id);
        categoria.setNombre(nombre);
        return CategoriaResponse.de(categoria, productos.countByCategoriaId(id));
    }

    public void eliminar(Long id) {
        Categoria categoria = buscarEntidad(id);
        if (productos.existsByCategoriaId(id)) {
            throw new ConflictoException(
                    "No se puede eliminar la categoría \"" + categoria.getNombre() + "\" porque tiene productos");
        }
        categorias.delete(categoria);
    }

    /**
     * Sin importar mayúsculas ni tildes ("ALGODON" = "Algodón"), igual que la importación desde Excel.
     * Una categoría sí puede cambiar solo sus mayúsculas o tildes ("Agujas" → "AGUJAS").
     */
    private void validarNombreUnico(String nombre, Long idActual) {
        String clave = TextoBusqueda.normalizar(nombre);
        categorias.findAll().stream()
                .filter(existente -> !existente.getId().equals(idActual))
                .filter(existente -> TextoBusqueda.normalizar(existente.getNombre()).equals(clave))
                .findFirst()
                .ifPresent(existente -> {
                    throw new ConflictoException("Ya existe la categoría \"" + existente.getNombre() + "\"");
                });
    }
}
