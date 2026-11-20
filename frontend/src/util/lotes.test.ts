import { describe, expect, it } from 'vitest'
import type { Lote, LoteEntregado } from '../api/tipos'
import { hoyLocal, opcionesDeLote, repartoEntregado, revisarReparto, todoDeUnLote } from './lotes'

const HOY = '2026-11-20'

const entregado = (loteId: number, numeroLote: string, fechaVencimiento: string, cantidad: number): LoteEntregado => ({
  loteId,
  numeroLote,
  fechaVencimiento,
  cantidad,
})

const lote = (id: number, numeroLote: string, fechaVencimiento: string, cantidad: number, vencido = false): Lote => ({
  id,
  numeroLote,
  fechaVencimiento,
  cantidad,
  costoPromedio: 1,
  vencido,
  diasParaVencer: null,
})

// Se entregaron 100: 3 sueltas (A, se acabaron) y 97 de la caja abierta (B, le quedan 3). C es una caja sellada.
const entregados = [entregado(1, 'A', '2027-03-31', 3), entregado(2, 'B', '2028-05-31', 97)]
const delProducto = [lote(2, 'B', '2028-05-31', 3), lote(3, 'C', '2029-01-31', 100), lote(4, 'V', '2026-01-31', 50, true)]

describe('cambiar de qué lotes sale un ítem', () => {
  it('ofrece los lotes que ya dieron y los del producto, sin los vencidos, primero el que vence antes', () => {
    const opciones = opcionesDeLote(entregados, delProducto, HOY)

    expect(opciones.map((o) => [o.numeroLote, o.entregado, o.maximo])).toEqual([
      ['A', 3, 3], // se acabó: solo puede volver a dar lo que dio
      ['B', 97, 100], // lo que le queda más lo que vuelve
      ['C', 0, 100],
    ])
  })

  it('de un lote entregado que ya venció solo puede quedar lo que se llevó', () => {
    const opciones = opcionesDeLote([entregado(4, 'V', '2026-01-31', 5)], delProducto, HOY)

    expect(opciones.find((o) => o.loteId === 4)).toMatchObject({ vencido: true, maximo: 5 })
  })

  it('empieza con lo entregado y "todo de un lote" deja los demás en 0', () => {
    const opciones = opcionesDeLote(entregados, delProducto, HOY)

    expect(revisarReparto(opciones, repartoEntregado(entregados))).toMatchObject({ suma: 100, igualAlEntregado: true })
    const todoDeC = todoDeUnLote(opciones, 3, 100)
    expect(revisarReparto(opciones, todoDeC)).toMatchObject({
      suma: 100,
      igualAlEntregado: false,
      excedidos: [],
      lotes: [{ loteId: 3, cantidad: 100 }],
    })
  })

  it('avisa si un lote da más de lo que tiene y cuenta los vacíos como 0', () => {
    const opciones = opcionesDeLote(entregados, delProducto, HOY)

    const revision = revisarReparto(opciones, { 1: '', 2: '120', 3: '' })
    expect(revision.suma).toBe(120)
    expect(revision.excedidos.map((o) => o.numeroLote)).toEqual(['B'])
  })

  it('arma la fecha de hoy en la zona del celular', () => {
    expect(hoyLocal(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05')
  })
})
