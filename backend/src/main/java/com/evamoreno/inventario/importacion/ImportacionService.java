package com.evamoreno.inventario.importacion;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.evamoreno.inventario.categoria.Categoria;
import com.evamoreno.inventario.categoria.CategoriaRepository;
import com.evamoreno.inventario.common.ConflictoException;
import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.common.TextoBusqueda;
import com.evamoreno.inventario.importacion.LectorExcelInventario.Fila;
import com.evamoreno.inventario.importacion.ResultadoImportacion.Observacion;
import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.producto.ProductoRepository;
import com.evamoreno.inventario.producto.ProductoRequest;
import com.evamoreno.inventario.producto.ProductoService;
import com.evamoreno.inventario.stock.EntradaRequest;
import com.evamoreno.inventario.stock.MovimientoService;
import com.evamoreno.inventario.stock.StockService;

/**
 * Importa el inventario desde la plantilla de Excel: crea las categorías y productos que falten
 * y registra una ENTRADA por cada lote con cantidad.
 *
 * <p>Todo ocurre en UNA transacción: si cualquier fila tiene un error, no se guarda nada.
 * Con {@code soloValidar} se ejecuta exactamente lo mismo y al final se deshace, así la validación
 * detecta los mismos problemas que la importación real.</p>
 */
@Service
public class ImportacionService {

    private static final Logger log = LoggerFactory.getLogger(ImportacionService.class);

    private final TransactionTemplate transaccion;
    private final CategoriaRepository categorias;
    private final ProductoRepository productos;
    private final ProductoService productoService;
    private final MovimientoService movimientoService;
    private final StockService stock;

    public ImportacionService(TransactionTemplate transaccion, CategoriaRepository categorias,
            ProductoRepository productos, ProductoService productoService, MovimientoService movimientoService,
            StockService stock) {
        this.transaccion = transaccion;
        this.categorias = categorias;
        this.productos = productos;
        this.productoService = productoService;
        this.movimientoService = movimientoService;
        this.stock = stock;
    }

    public ResultadoImportacion importar(InputStream archivo, boolean soloValidar) {
        LectorExcelInventario.Lectura lectura = LectorExcelInventario.leer(archivo);
        if (lectura.filas().isEmpty() && lectura.errores().isEmpty()) {
            return new ResultadoImportacion(false, soloValidar, 0, 0, 0, 0, 0, 0,
                    List.of(new Observacion(0, "La hoja \"" + LectorExcelInventario.HOJA + "\" no tiene filas con datos")),
                    List.of());
        }
        return transaccion.execute(status -> {
            Proceso proceso = new Proceso(lectura);
            proceso.ejecutar();
            boolean guardar = !soloValidar && proceso.errores.isEmpty();
            if (!guardar) {
                status.setRollbackOnly(); // deshace todo lo que se hizo en esta transacción
            }
            return proceso.resultado(guardar, soloValidar);
        });
    }

    /** Estado de una importación en curso. */
    private final class Proceso {

        private final List<Fila> filas;
        private final List<Observacion> errores;
        private final List<Observacion> advertencias = new ArrayList<>();
        private final Map<String, Categoria> categoriasPorNombre = new HashMap<>();
        private final Set<Long> creados = new HashSet<>();
        private final Set<Long> existentes = new HashSet<>();
        private int categoriasCreadas;
        private int entradas;
        private long unidades;

        Proceso(LectorExcelInventario.Lectura lectura) {
            this.filas = lectura.filas();
            this.errores = new ArrayList<>(lectura.errores());
            categorias.findAll().forEach(c -> categoriasPorNombre.put(TextoBusqueda.normalizar(c.getNombre()), c));
        }

        void ejecutar() {
            for (Fila fila : filas) {
                try {
                    procesar(fila);
                } catch (ConflictoException | RecursoNoEncontradoException e) {
                    errores.add(new Observacion(fila.numero(), e.getMessage()));
                } catch (DataAccessException e) {
                    // Un error de la base de datos invalida la transacción: no tiene sentido seguir
                    log.warn("Error de base de datos importando la fila {}", fila.numero(), e);
                    errores.add(new Observacion(fila.numero(),
                            "Error al guardar esta fila; se detuvo la importación. Revisa los datos."));
                    return;
                }
            }
        }

        private void procesar(Fila fila) {
            Long productoId = buscarOCrearProducto(fila, categoria(fila.categoria()));
            if (fila.cantidad() > 0) {
                movimientoService.registrarEntrada(new EntradaRequest(productoId, fila.numeroLote(),
                        fila.fechaVencimiento(), fila.cantidad(), "Importación de inventario (fila " + fila.numero() + ")"));
                entradas++;
                unidades += fila.cantidad();
            }
        }

        /** Busca la categoría sin importar tildes ni mayúsculas; si no existe, la crea. */
        private Categoria categoria(String nombre) {
            return categoriasPorNombre.computeIfAbsent(TextoBusqueda.normalizar(nombre), clave -> {
                categoriasCreadas++;
                return categorias.save(new Categoria(nombre));
            });
        }

        private Long buscarOCrearProducto(Fila fila, Categoria categoria) {
            String clave = Producto.calcularClaveUnica(fila.nombre(), fila.marca(), fila.presentacion());
            Producto existente = fila.codigo() != null
                    ? productos.findByCodigoIgnoreCase(fila.codigo()).orElse(null)
                    : productos.findByClaveUnica(clave).orElse(null);

            if (existente == null) {
                Long id = productoService.crear(new ProductoRequest(fila.codigo(), fila.nombre(), fila.marca(),
                        categoria.getId(), fila.presentacion(), fila.unidad(), fila.stockMinimo(),
                        fila.codigosBarras())).id();
                creados.add(id);
                return id;
            }
            if (!existente.getClaveUnica().equals(clave)) {
                throw new ConflictoException("El código " + fila.codigo() + " ya pertenece a \""
                        + existente.getNombre() + " · " + existente.getMarca() + " · " + existente.getPresentacion()
                        + "\"");
            }
            if (!creados.contains(existente.getId()) && existentes.add(existente.getId())) {
                int stockPrevio = stock.disponible(existente.getId());
                if (stockPrevio > 0) {
                    advertencias.add(new Observacion(fila.numero(), "\"" + existente.getNombre()
                            + "\" ya tenía " + stockPrevio + " unidades; se sumarán las de este archivo. "
                            + "Si este archivo ya se importó antes, el stock quedará duplicado."));
                }
            }
            return existente.getId();
        }

        ResultadoImportacion resultado(boolean importado, boolean soloValidar) {
            return new ResultadoImportacion(importado, soloValidar, filas.size() + contarFilasConError(),
                    creados.size(), existentes.size(), categoriasCreadas, entradas, unidades,
                    List.copyOf(errores), List.copyOf(advertencias));
        }

        private int contarFilasConError() {
            Set<Integer> filasValidas = new HashSet<>();
            filas.forEach(f -> filasValidas.add(f.numero()));
            return (int) errores.stream().map(Observacion::fila).filter(f -> !filasValidas.contains(f)).distinct()
                    .count();
        }
    }
}
