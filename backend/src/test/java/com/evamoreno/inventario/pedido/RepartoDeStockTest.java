package com.evamoreno.inventario.pedido;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.evamoreno.inventario.pedido.RepartoDeStock.Demanda;

class RepartoDeStockTest {

    private static final long GUANTES = 1L;
    private static final long JERINGAS = 2L;

    @Test
    void elPedidoMasAntiguoSeparaPrimeroYElSiguienteVeLoQueQueda() {
        // Hay 10 guantes: el primer ítem pide 8 y el segundo 6
        Map<Long, Integer> asignado = RepartoDeStock.repartir(
                List.of(new Demanda(101L, GUANTES, 8), new Demanda(102L, GUANTES, 6)),
                Map.of(GUANTES, 10));

        assertThat(asignado).containsEntry(101L, 8).containsEntry(102L, 2);
    }

    @Test
    void cadaProductoSeReparteAparte() {
        Map<Long, Integer> asignado = RepartoDeStock.repartir(
                List.of(new Demanda(101L, GUANTES, 5), new Demanda(102L, JERINGAS, 5),
                        new Demanda(103L, GUANTES, 5)),
                Map.of(GUANTES, 7, JERINGAS, 20));

        assertThat(asignado).containsEntry(101L, 5).containsEntry(102L, 5).containsEntry(103L, 2);
    }

    @Test
    void sinStockNoSeAsignaNada() {
        Map<Long, Integer> asignado = RepartoDeStock.repartir(
                List.of(new Demanda(101L, GUANTES, 3), new Demanda(102L, GUANTES, 1)), Map.of());

        assertThat(asignado).containsEntry(101L, 0).containsEntry(102L, 0);
    }

    @Test
    void cuandoElPrimeroAgotaElStockElSiguienteQuedaEnCero() {
        Map<Long, Integer> asignado = RepartoDeStock.repartir(
                List.of(new Demanda(101L, GUANTES, 10), new Demanda(102L, GUANTES, 4)), Map.of(GUANTES, 10));

        assertThat(asignado).containsEntry(101L, 10).containsEntry(102L, 0);
    }
}
