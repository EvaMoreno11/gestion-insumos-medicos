import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { movimientos } from '../api/recursos'
import type { MovimientoHistorial } from '../api/tipos'
import { useListaConVerMas } from '../hooks'
import { diaLocal, dinero, hora, mesAnio, nombreDeFecha, unir } from '../util/formato'
import { Aviso, Boton, EstadoCarga, Etiqueta, Tarjeta } from './basicos'

/** Nota con que se registra la baja de un lote vencido (la misma del backend). */
const NOTA_BAJA = 'Baja por vencido'

type Clase = 'entrada' | 'inicial' | 'salida' | 'devolucion' | 'ajuste' | 'baja'

const ETIQUETA: Record<Clase, { texto: string; color: 'verde' | 'gris' | 'azul' | 'ambar' | 'rojo'; numero: string }> = {
  entrada: { texto: 'Entrada', color: 'verde', numero: 'text-emerald-700' },
  inicial: { texto: 'Carga inicial', color: 'gris', numero: 'text-slate-700' },
  salida: { texto: 'Salida', color: 'gris', numero: 'text-slate-900' },
  devolucion: { texto: 'Devolución', color: 'azul', numero: 'text-marca' },
  ajuste: { texto: 'Ajuste', color: 'ambar', numero: 'text-amber-800' },
  baja: { texto: 'Baja por vencido', color: 'rojo', numero: 'text-red-700' },
}

function claseDe(m: MovimientoHistorial): Clase {
  if (m.tipo === 'ENTRADA') {
    return m.inicial ? 'inicial' : 'entrada'
  }
  if (m.tipo === 'SALIDA') {
    return 'salida'
  }
  if (m.tipo === 'DEVOLUCION') {
    return 'devolucion'
  }
  return m.nota === NOTA_BAJA ? 'baja' : 'ajuste'
}

/**
 * Historial de un producto (uso interno): todo lo que le pasó, de lo más reciente a lo más antiguo y separado por
 * día. Entradas con su costo, salidas y devoluciones con su nota y cliente, ajustes con su motivo y bajas por
 * vencido, cada uno con cuánto quedó en su lote. Sirve para entender por qué el stock es el que es.
 */
export function HistorialProducto({ productoId }: { productoId: number }) {
  const { datos, cargando, error, lista, verMas, cargandoMas, errorMas } = useListaConVerMas(
    (pagina) => movimientos.historial(productoId, pagina),
    String(productoId),
    (r) => r.contenido,
  )

  const dias: { fecha: string; movimientos: MovimientoHistorial[] }[] = []
  for (const m of lista) {
    const fecha = diaLocal(new Date(m.fecha))
    if (dias.at(-1)?.fecha !== fecha) {
      dias.push({ fecha, movimientos: [] })
    }
    dias.at(-1)?.movimientos.push(m)
  }
  const total = datos?.totalElementos ?? 0

  return (
    <Tarjeta>
      <h3 className="font-semibold">Historial</h3>
      <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
        {total === 0 && <p className="mt-2 text-sm text-slate-500">Todavía no tiene movimientos.</p>}
        {dias.map(({ fecha, movimientos: delDia }) => (
          <section key={fecha}>
            <p className="mt-3 text-xs font-semibold uppercase tracking-wide text-slate-500">{nombreDeFecha(fecha)}</p>
            <ul className="divide-y divide-slate-100">
              {delDia.map((m) => (
                <li key={m.id}>
                  <FilaMovimiento movimiento={m} />
                </li>
              ))}
            </ul>
          </section>
        ))}
        {errorMas && (
          <div className="mt-2">
            <Aviso tipo="error">{errorMas}</Aviso>
          </div>
        )}
        {lista.length < total && (
          <Boton variante="secundario" ancho className="mt-2" disabled={cargandoMas} onClick={() => void verMas()}>
            {cargandoMas ? 'Cargando…' : `Ver más (${total - lista.length} más)`}
          </Boton>
        )}
      </EstadoCarga>
    </Tarjeta>
  )
}

function FilaMovimiento({ movimiento: m }: { movimiento: MovimientoHistorial }) {
  const clase = claseDe(m)
  const { texto, color, numero } = ETIQUETA[clase]
  const lote = m.numeroLote ? `Lote ${m.numeroLote}` : 'Sin número de lote'
  const quedan = `quedan ${m.stockResultanteLote} en el lote`
  const nota = m.pedidoNumero ? `Nota ${m.pedidoNumero}` : 'Pedido sin número (solo tabla)'

  let principal: ReactNode
  let detalle: string
  if (clase === 'entrada' || clase === 'inicial') {
    principal = unir(' · ', lote, m.costoUnitario !== null ? `${dinero(m.costoUnitario)} c/u` : null)
    detalle = `Quedan ${m.stockResultanteLote} en el lote`
  } else if ((clase === 'salida' || clase === 'devolucion') && m.pedidoId !== null) {
    principal = (
      <Link to={`/pedidos/${m.pedidoId}`} className="text-marca">
        {unir(' · ', clase === 'devolucion' ? `Corrección de la ${nota.toLowerCase()}` : nota, m.cliente)}
      </Link>
    )
    detalle = `${lote} · ${quedan}`
  } else if (clase === 'baja') {
    principal = unir(' · ', lote, m.fechaVencimiento ? `venció en ${mesAnio(m.fechaVencimiento)}` : null)
    detalle = `Quedan ${m.stockResultanteLote} en el lote`
  } else {
    principal = m.nota ? `«${m.nota}»` : lote
    detalle = m.nota ? `${lote} · ${quedan}` : quedan
  }
  const signo = m.tipo === 'SALIDA' ? '−' : m.cantidad > 0 ? '+' : '−'

  return (
    <div className="flex items-start gap-3 py-2.5">
      <span className="w-11 shrink-0 pt-0.5 text-sm text-slate-500 tabular-nums">{hora(m.fecha)}</span>
      <span className="min-w-0 flex-1">
        <Etiqueta color={color}>{texto}</Etiqueta>
        <span className="mt-1 block text-sm break-words text-slate-700">{principal}</span>
        <span className="block text-sm text-slate-500">{detalle}</span>
      </span>
      <span className={`shrink-0 text-lg font-bold tabular-nums ${numero}`}>
        {signo}
        {Math.abs(m.cantidad)}
      </span>
    </div>
  )
}
