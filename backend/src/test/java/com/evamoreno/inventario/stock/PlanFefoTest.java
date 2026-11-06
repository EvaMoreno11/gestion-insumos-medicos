package com.evamoreno.inventario.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class PlanFefoTest {

    private static Lote lote(String numero, int cantidad) {
        Lote lote = new Lote(null, numero, LocalDate.of(2027, 1, 1));
        lote.sumar(cantidad);
        return lote;
    }

    @Test
    void tomaPrimeroDelLoteQueVenceAntes() {
        Lote primero = lote("A", 4);
        Lote segundo = lote("B", 10);

        PlanFefo plan = PlanFefo.calcular(List.of(primero, segundo), 6);

        assertThat(plan.alcanza()).isTrue();
        assertThat(plan.asignaciones()).extracting(a -> a.lote().getNumeroLote(), PlanFefo.Asignacion::cantidad)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A", 4),
                        org.assertj.core.groups.Tuple.tuple("B", 2));
    }

    @Test
    void siUnLoteAlcanzaNoTocaLosDemas() {
        PlanFefo plan = PlanFefo.calcular(List.of(lote("A", 10), lote("B", 10)), 3);

        assertThat(plan.asignaciones()).hasSize(1);
        assertThat(plan.asignaciones().getFirst().cantidad()).isEqualTo(3);
    }

    @Test
    void informaCuantoFaltaSiNoAlcanza() {
        PlanFefo plan = PlanFefo.calcular(List.of(lote("A", 2), lote("B", 3)), 8);

        assertThat(plan.alcanza()).isFalse();
        assertThat(plan.cantidadCubierta()).isEqualTo(5);
        assertThat(plan.faltante()).isEqualTo(3);
    }

    @Test
    void sinLotesFaltaTodo() {
        PlanFefo plan = PlanFefo.calcular(List.of(), 4);

        assertThat(plan.asignaciones()).isEmpty();
        assertThat(plan.faltante()).isEqualTo(4);
    }

    @Test
    void rechazaCantidadesNoPositivas() {
        assertThatThrownBy(() -> PlanFefo.calcular(List.of(lote("A", 1)), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
