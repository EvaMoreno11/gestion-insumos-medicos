package com.evamoreno.inventario.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.util.List;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class PlanFefoTest {

    private static final LocalDate MARZO_2027 = LocalDate.of(2027, 3, 31);
    private static final LocalDate ENERO_2028 = LocalDate.of(2028, 1, 31);

    private static Lote lote(String numero, LocalDate vence, int cantidad) {
        Lote lote = new Lote(null, numero, vence);
        lote.sumar(cantidad);
        return lote;
    }

    private static Lote lote(String numero, int cantidad) {
        return lote(numero, MARZO_2027, cantidad);
    }

    private static Tuple[] plan(List<Lote> lotes, int cantidad) {
        return PlanFefo.calcular(lotes, cantidad).asignaciones().stream()
                .map(a -> tuple(a.lote().getNumeroLote(), a.cantidad()))
                .toArray(Tuple[]::new);
    }

    @Test
    void tomaPrimeroDelLoteQueVenceAntesAunqueTengaMenos() {
        Lote vencePronto = lote("A", MARZO_2027, 4);
        Lote venceDespues = lote("B", ENERO_2028, 10);

        PlanFefo plan = PlanFefo.calcular(List.of(vencePronto, venceDespues), 6);

        assertThat(plan.alcanza()).isTrue();
        assertThat(plan.asignaciones()).extracting(a -> a.lote().getNumeroLote(), PlanFefo.Asignacion::cantidad)
                .containsExactly(tuple("A", 4), tuple("B", 2));
    }

    @Test
    void conElMismoVencimientoSaleTodoDelLoteQueAlcanzaSolo() {
        // 3 sueltas que ya estaban y una caja de 100 que llegó después, las dos vencen 03/2027
        List<Lote> lotes = List.of(lote("SUELTAS", 3), lote("CAJA", 100));

        assertThat(plan(lotes, 100)).containsExactly(tuple("CAJA", 100)); // la caja entera, sin abrirla
        assertThat(plan(lotes, 10)).containsExactly(tuple("CAJA", 10)); // de un solo lote, no 3 + 7
    }

    @Test
    void siVariosAlcanzanSaleDelMasChicoParaNoAbrirLaCaja() {
        List<Lote> lotes = List.of(lote("CAJA", 100), lote("SUELTAS", 3));

        assertThat(plan(lotes, 2)).containsExactly(tuple("SUELTAS", 2));
    }

    @Test
    void siNingunoAlcanzaSoloEmpiezaPorElMasGrande() {
        List<Lote> lotes = List.of(lote("SUELTAS", 3), lote("CAJA", 100));

        assertThat(plan(lotes, 102)).containsExactly(tuple("CAJA", 100), tuple("SUELTAS", 2));
    }

    @Test
    void conTresLotesDelMismoMesUsaLaMenorCantidadDeLotes() {
        List<Lote> lotes = List.of(lote("A", 3), lote("B", 50), lote("C", 50));

        // 50 de un lote y los 10 que faltan del otro de 50 (no 3 + 50 + 7)
        assertThat(plan(lotes, 60)).containsExactly(tuple("B", 50), tuple("C", 10));
    }

    @Test
    void elMismoVencimientoNoSaltaAUnLoteQueVenceAntes() {
        // Lo que vence antes sale primero aunque se parta; la regla solo ordena lotes del mismo mes
        List<Lote> lotes = List.of(lote("VIEJO", MARZO_2027, 3), lote("CAJA_1", ENERO_2028, 100),
                lote("CAJA_2", ENERO_2028, 100));

        assertThat(plan(lotes, 100)).containsExactly(tuple("VIEJO", 3), tuple("CAJA_1", 97));
    }

    @Test
    void siDosLotesAlcanzanYTienenLoMismoSaleDelPrimero() {
        assertThat(plan(List.of(lote("A", 10), lote("B", 10)), 3)).containsExactly(tuple("A", 3));
    }

    @Test
    void losLotesSinVencimientoSeTratanComoDelMismoMes() {
        List<Lote> lotes = List.of(lote("SUELTAS", null, 3), lote("CAJA", null, 50));

        assertThat(plan(lotes, 50)).containsExactly(tuple("CAJA", 50));
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
