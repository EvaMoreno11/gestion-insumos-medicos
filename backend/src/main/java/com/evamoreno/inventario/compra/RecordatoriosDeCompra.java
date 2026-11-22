package com.evamoreno.inventario.compra;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.common.RecursoNoEncontradoException;
import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.producto.ProductoRepository;

/**
 * Productos agregados a mano a "Por comprar". Agregar uno que ya está cambia su cantidad y su nota (no se repite).
 * Se quitan a mano o solos, al registrar una entrada de ese producto: ya se compró.
 */
@Service
@Transactional
public class RecordatoriosDeCompra {

    private final RecordatorioCompraRepository recordatorios;
    private final ProductoRepository productos;

    public RecordatoriosDeCompra(RecordatorioCompraRepository recordatorios, ProductoRepository productos) {
        this.recordatorios = recordatorios;
        this.productos = productos;
    }

    public void guardar(Long productoId, RecordatorioCompraRequest r) {
        RecordatorioCompra recordatorio = recordatorios.findByProductoId(productoId).orElseGet(() -> {
            Producto producto = productos.findById(productoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el producto con id " + productoId));
            return recordatorios.save(new RecordatorioCompra(producto));
        });
        recordatorio.cambiar(r.cantidad(), r.nota());
    }

    /** Quita el producto de lo agregado a mano (si estaba). Lo que falta para pedidos sigue en la lista. */
    public void quitar(Long productoId) {
        recordatorios.findByProductoId(productoId).ifPresent(recordatorios::delete);
    }

    @Transactional(readOnly = true)
    public List<RecordatorioCompra> todos() {
        return recordatorios.todos();
    }
}
