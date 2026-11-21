import type { Lote, LoteEntregado } from '../api/tipos'

/** Un lote del que puede salir un ítem al corregir de qué lotes salió. */
export interface OpcionLote {
  loteId: number
  numeroLote: string | null
  fechaVencimiento: string | null
  vencido: boolean
  /** Lo que este ítem ya se llevó de ese lote */
  entregado: number
  /** Lo máximo que puede dar: su stock más lo que vuelve de este ítem (de un vencido, solo lo que ya se llevó) */
  maximo: number
}

/** Unidades por lote tal como se escriben (loteId → texto) */
export type Reparto = Record<number, string>

/**
 * Lotes entre los que se puede repartir un ítem: los que ya le dieron unidades y los del producto que no están
 * vencidos, primero los que vencen antes.
 *
 * @param hoy fecha local aaaa-mm-dd (un lote vence el último día de su mes)
 */
export function opcionesDeLote(entregados: LoteEntregado[], delProducto: Lote[], hoy: string): OpcionLote[] {
  const porId = new Map<number, OpcionLote>()
  for (const e of entregados) {
    const vencido = e.fechaVencimiento !== null && e.fechaVencimiento < hoy
    porId.set(e.loteId, {
      loteId: e.loteId,
      numeroLote: e.numeroLote,
      fechaVencimiento: e.fechaVencimiento,
      vencido,
      entregado: e.cantidad,
      maximo: e.cantidad,
    })
  }
  for (const l of delProducto) {
    const ya = porId.get(l.id)
    if (ya) {
      ya.vencido = l.vencido
      ya.maximo = l.vencido ? ya.entregado : ya.entregado + l.cantidad
    } else if (!l.vencido) {
      porId.set(l.id, {
        loteId: l.id,
        numeroLote: l.numeroLote,
        fechaVencimiento: l.fechaVencimiento,
        vencido: false,
        entregado: 0,
        maximo: l.cantidad,
      })
    }
  }
  return [...porId.values()].sort(
    (a, b) =>
      (a.fechaVencimiento ?? '9999').localeCompare(b.fechaVencimiento ?? '9999') ||
      (a.numeroLote ?? '').localeCompare(b.numeroLote ?? ''),
  )
}

/** El reparto con el que se entregó el ítem (para empezar a cambiarlo desde ahí). */
export function repartoEntregado(entregados: LoteEntregado[]): Reparto {
  return Object.fromEntries(entregados.map((e) => [e.loteId, String(e.cantidad)]))
}

/** Todo de un lote: ese con la cantidad completa y los demás en 0. */
export function todoDeUnLote(opciones: OpcionLote[], loteId: number, cantidad: number): Reparto {
  return Object.fromEntries(opciones.map((o) => [o.loteId, o.loteId === loteId ? String(cantidad) : '0']))
}

/** Cuánto suma el reparto, qué lotes piden más de lo que tienen y si es igual a como se entregó. */
export function revisarReparto(opciones: OpcionLote[], reparto: Reparto) {
  const asignado = (o: OpcionLote) => Number(reparto[o.loteId] || 0)
  return {
    suma: opciones.reduce((s, o) => s + asignado(o), 0),
    excedidos: opciones.filter((o) => asignado(o) > o.maximo),
    igualAlEntregado: opciones.every((o) => asignado(o) === o.entregado),
    /** Lo que se envía al servidor: solo los lotes con unidades */
    lotes: opciones.filter((o) => asignado(o) > 0).map((o) => ({ loteId: o.loteId, cantidad: asignado(o) })),
  }
}
