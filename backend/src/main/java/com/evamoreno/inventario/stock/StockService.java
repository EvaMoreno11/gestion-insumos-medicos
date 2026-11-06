package com.evamoreno.inventario.stock;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cálculos de stock compartidos por productos, alertas y movimientos.
 * Stock disponible = suma de los lotes NO vencidos (lo vencido no se puede despachar).
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private final LoteRepository lotes;
    private final Clock clock;

    public StockService(LoteRepository lotes, Clock clock) {
        this.lotes = lotes;
        this.clock = clock;
    }

    public LocalDate hoy() {
        return LocalDate.now(clock);
    }

    /** Stock disponible por producto. Los productos sin lotes no aparecen en el mapa (= 0). */
    public Map<Long, Integer> disponiblePorProducto(Collection<Long> productoIds) {
        if (productoIds.isEmpty()) {
            return Map.of();
        }
        return lotes.stockDisponible(productoIds, hoy()).stream()
                .collect(Collectors.toMap(LoteRepository.StockProducto::getProductoId,
                        s -> s.getDisponible().intValue()));
    }

    public int disponible(Long productoId) {
        return disponiblePorProducto(List.of(productoId)).getOrDefault(productoId, 0);
    }

    /** Lotes con unidades (vencidos incluidos), en orden FEFO. */
    public List<Lote> lotesConStock(Long productoId) {
        return lotes.conStock(productoId);
    }
}
