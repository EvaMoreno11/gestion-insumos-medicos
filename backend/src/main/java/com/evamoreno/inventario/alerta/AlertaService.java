package com.evamoreno.inventario.alerta;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.evamoreno.inventario.producto.Producto;
import com.evamoreno.inventario.producto.ProductoRepository;
import com.evamoreno.inventario.producto.ProductoResumen;
import com.evamoreno.inventario.stock.LoteRepository;
import com.evamoreno.inventario.stock.StockComprometido;
import com.evamoreno.inventario.stock.StockService;

@Service
@Transactional(readOnly = true)
public class AlertaService {

    private final ProductoRepository productos;
    private final LoteRepository lotes;
    private final StockService stock;
    private final StockComprometido comprometido;

    public AlertaService(ProductoRepository productos, LoteRepository lotes, StockService stock,
            StockComprometido comprometido) {
        this.productos = productos;
        this.lotes = lotes;
        this.stock = stock;
        this.comprometido = comprometido;
    }

    /** Productos activos con stock disponible menor o igual al mínimo. Primero los agotados. */
    public List<ProductoResumen> stockBajo() {
        List<Long> ids = productos.idsConStockBajo(stock.hoy());
        Map<Long, Integer> disponibles = stock.disponiblePorProducto(ids);
        Map<Long, Integer> pedidos = comprometido.pedidoEnBorradores(ids);
        return productos.findAllByIdIn(ids).stream()
                .map((Producto p) -> ProductoResumen.de(p, disponibles.getOrDefault(p.getId(), 0),
                        pedidos.getOrDefault(p.getId(), 0)))
                .sorted(Comparator.comparingInt(ProductoResumen::stockDisponible)
                        .thenComparing(ProductoResumen::nombre))
                .toList();
    }

    /** Lotes con unidades que vencen entre hoy y dentro de {@code dias} días. */
    public List<LoteAlerta> porVencer(int dias) {
        LocalDate hoy = stock.hoy();
        return lotes.porVencer(hoy, hoy.plusDays(dias)).stream().map(l -> LoteAlerta.de(l, hoy)).toList();
    }

    /** Lotes vencidos que todavía tienen unidades (hay que darlos de baja con un ajuste). */
    public List<LoteAlerta> vencidos() {
        LocalDate hoy = stock.hoy();
        return lotes.vencidos(hoy).stream().map(l -> LoteAlerta.de(l, hoy)).toList();
    }
}
