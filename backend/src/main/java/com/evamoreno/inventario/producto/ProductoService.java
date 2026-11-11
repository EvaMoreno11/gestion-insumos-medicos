package com.evamoreno.inventario.producto;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.categoria.Categoria;
import com.evamoreno.inventario.categoria.CategoriaService;
import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.Pagina;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.precio.CategoriaPrecioService;
import com.evamoreno.inventario.stock.StockComprometido;
import com.evamoreno.inventario.stock.StockService;

@Service
@Transactional
public class ProductoService {

    static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final Sort ORDEN = Sort.by("nombre", "marca", "presentacion");

    private final ProductoRepository productos;
    private final CodigoBarrasRepository codigosBarras;
    private final CategoriaService categorias;
    private final StockService stock;
    private final CategoriaPrecioService categoriasPrecio;
    private final StockComprometido comprometido;

    public ProductoService(ProductoRepository productos, CodigoBarrasRepository codigosBarras,
            CategoriaService categorias, StockService stock, CategoriaPrecioService categoriasPrecio,
            StockComprometido comprometido) {
        this.productos = productos;
        this.codigosBarras = codigosBarras;
        this.categorias = categorias;
        this.stock = stock;
        this.categoriasPrecio = categoriasPrecio;
        this.comprometido = comprometido;
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public Pagina<ProductoResumen> buscar(String buscar, Long categoriaId, boolean incluirInactivos,
            int pagina, int tamano) {
        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0),
                Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA), ORDEN);
        Page<Producto> page = productos.findAll(
                ProductoEspecificaciones.filtrar(buscar, categoriaId, incluirInactivos), pageRequest);
        // Stock de toda la página en UNA consulta (no una por producto)
        List<Long> ids = page.getContent().stream().map(Producto::getId).toList();
        Map<Long, Integer> disponibles = stock.disponiblePorProducto(ids);
        Map<Long, Integer> pedidos = comprometido.pedidoEnBorradores(ids);
        return Pagina.de(page, p -> ProductoResumen.de(p, disponibles.getOrDefault(p.getId(), 0),
                pedidos.getOrDefault(p.getId(), 0)));
    }

    @Transactional(readOnly = true)
    public ProductoDetalle obtener(Long id) {
        return detalle(buscarEntidad(id));
    }

    @Transactional(readOnly = true)
    public ProductoDetalle obtenerPorCodigoBarras(String codigo) {
        ProductoCodigoBarras codigoBarras = codigosBarras.findByCodigo(codigo.trim())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Ningún producto tiene el código de barras " + codigo));
        return detalle(codigoBarras.getProducto());
    }

    // ------------------------------------------------------------------ cambios

    public ProductoDetalle crear(ProductoRequest request) {
        Categoria categoria = categorias.buscarEntidad(request.categoriaId());
        DatosLimpios datos = DatosLimpios.de(request);

        validarProductoUnico(datos, null);
        String codigo = datos.codigo() == null ? generarCodigo(categoria) : datos.codigo();
        validarCodigoUnico(codigo, null);
        validarCodigosBarras(datos.codigosBarras(), null);

        Producto producto = new Producto(codigo, datos.nombre(), datos.marca(), datos.presentacion(),
                datos.unidadMedida(), datos.stockMinimo(), categoria);
        producto.setCosto(datos.costo());
        producto.reemplazarCodigosBarras(datos.codigosBarras());
        return detalle(productos.save(producto));
    }

    public ProductoDetalle actualizar(Long id, ProductoRequest request) {
        Producto producto = buscarEntidad(id);
        Categoria categoria = categorias.buscarEntidad(request.categoriaId());
        DatosLimpios datos = DatosLimpios.de(request);

        validarProductoUnico(datos, id);
        // Código vacío en una actualización = conservar el actual
        if (datos.codigo() != null && !datos.codigo().equals(producto.getCodigo())) {
            validarCodigoUnico(datos.codigo(), id);
            producto.setCodigo(datos.codigo());
        }
        validarCodigosBarras(datos.codigosBarras(), id);

        producto.actualizarDatos(datos.nombre(), datos.marca(), datos.presentacion(), datos.unidadMedida(),
                datos.stockMinimo(), categoria);
        producto.setCosto(datos.costo());
        producto.reemplazarCodigosBarras(datos.codigosBarras());
        productos.flush(); // para que la respuesta traiga la fecha de actualización
        return detalle(producto);
    }

    /** Cambia solo el costo (lo usa la importación desde Excel). */
    public void actualizarCosto(Long id, BigDecimal costo) {
        productos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el producto con id " + id))
                .setCosto(costo);
    }

    /** Los productos no se borran (tendrán historial de movimientos): se desactivan. */
    public ProductoDetalle cambiarEstado(Long id, boolean activo) {
        Producto producto = buscarEntidad(id);
        producto.setActivo(activo);
        productos.flush();
        return detalle(producto);
    }

    // ------------------------------------------------------------------ reglas

    private ProductoDetalle detalle(Producto producto) {
        return ProductoDetalle.de(producto, stock.lotesConStock(producto.getId()), stock.hoy(),
                categoriasPrecio.preciosPorCategoria(producto.getCosto()),
                comprometido.pedidoEnBorradores(List.of(producto.getId())).getOrDefault(producto.getId(), 0));
    }

    private Producto buscarEntidad(Long id) {
        return productos.findWithDetalleById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el producto con id " + id));
    }

    private void validarProductoUnico(DatosLimpios datos, Long idActual) {
        String clave = Producto.calcularClaveUnica(datos.nombre(), datos.marca(), datos.presentacion());
        productos.findByClaveUnica(clave)
                .filter(existente -> !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictoException("Ya existe este producto con la misma marca y presentación (código "
                            + existente.getCodigo() + ")");
                });
    }

    private void validarCodigoUnico(String codigo, Long idActual) {
        productos.findByCodigoIgnoreCase(codigo)
                .filter(existente -> !existente.getId().equals(idActual))
                .ifPresent(existente -> {
                    throw new ConflictoException("Ya existe un producto con el código " + codigo);
                });
    }

    private void validarCodigosBarras(List<String> codigos, Long idActual) {
        for (String codigo : codigos) {
            codigosBarras.findByCodigo(codigo)
                    .filter(existente -> !existente.getProducto().getId().equals(idActual))
                    .ifPresent(existente -> {
                        throw new ConflictoException("El código de barras " + codigo
                                + " ya pertenece al producto " + existente.getProducto().getCodigo());
                    });
        }
    }

    /** Código interno automático: 3 letras de la categoría + número correlativo. Ej: JER-00001. */
    private String generarCodigo(Categoria categoria) {
        String letras = TextoBusqueda.normalizar(categoria.getNombre()).replaceAll("[^a-z]", "");
        String prefijo = (letras + "xxx").substring(0, 3).toUpperCase(Locale.ROOT);
        return "%s-%05d".formatted(prefijo, productos.siguienteNumeroCodigo());
    }

    /** Datos del request ya limpios: sin espacios de más, código en mayúsculas, sin duplicados. */
    private record DatosLimpios(String codigo, String nombre, String marca, String presentacion,
            String unidadMedida, int stockMinimo, BigDecimal costo, List<String> codigosBarras) {

        static DatosLimpios de(ProductoRequest r) {
            String codigo = r.codigo() == null || r.codigo().isBlank()
                    ? null
                    : r.codigo().trim().toUpperCase(Locale.ROOT);
            List<String> barras = r.codigosBarras() == null
                    ? List.of()
                    : List.copyOf(new LinkedHashSet<>(r.codigosBarras().stream()
                            .filter(Objects::nonNull).map(String::trim).toList()));
            return new DatosLimpios(codigo,
                    TextoBusqueda.limpiar(r.nombre()),
                    TextoBusqueda.limpiar(r.marca()),
                    TextoBusqueda.limpiar(r.presentacion()),
                    TextoBusqueda.limpiar(r.unidadMedida()),
                    r.stockMinimo() == null ? 0 : r.stockMinimo(),
                    r.costo(),
                    barras);
        }
    }
}
