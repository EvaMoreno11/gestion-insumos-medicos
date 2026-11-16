import { useState, type ReactNode } from 'react'
import type { ConsultaPeriodo } from '../api/recursos'
import type { Resumen, TipoPeriodo } from '../api/tipos'
import { dinero, mesCorto, mesLargo, variacion } from '../util/formato'
import { Boton, Tarjeta } from './basicos'

// ---------------------------------------------------------------- periodo

const PERIODOS: { valor: TipoPeriodo; titulo: string }[] = [
  { valor: 'MES', titulo: 'Este mes' },
  { valor: 'MES_ANTERIOR', titulo: 'Mes pasado' },
  { valor: 'ANIO', titulo: 'Este año' },
  { valor: 'RANGO', titulo: 'Fechas' },
]

/** Hoy en la hora del dispositivo, como aaaa-mm-dd. */
function hoyLocal(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Elegir el periodo: este mes, el mes pasado, este año o un rango de fechas. */
export function SelectorPeriodo({
  consulta,
  onCambio,
}: {
  consulta: ConsultaPeriodo
  onCambio: (consulta: ConsultaPeriodo) => void
}) {
  const [desde, setDesde] = useState(consulta.desde ?? '')
  const [hasta, setHasta] = useState(consulta.hasta ?? '')

  return (
    <div className="space-y-2">
      <div className="grid grid-cols-4 gap-1 rounded-xl bg-slate-200 p-1 lg:max-w-xl" role="tablist" aria-label="Periodo">
        {PERIODOS.map((p) => (
          <button
            key={p.valor}
            type="button"
            role="tab"
            aria-selected={consulta.periodo === p.valor}
            onClick={() => {
              if (p.valor !== 'RANGO') {
                onCambio({ periodo: p.valor })
                return
              }
              const hoy = hoyLocal()
              const inicio = desde || `${hoy.slice(0, 8)}01`
              const fin = hasta || hoy
              setDesde(inicio)
              setHasta(fin)
              onCambio({ periodo: 'RANGO', desde: inicio, hasta: fin })
            }}
            className={`min-h-10 rounded-lg px-1 text-sm font-semibold ${
              consulta.periodo === p.valor ? 'bg-white text-marca shadow-sm' : 'text-slate-600'
            }`}
          >
            {p.titulo}
          </button>
        ))}
      </div>
      {consulta.periodo === 'RANGO' && (
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            if (desde && hasta) {
              onCambio({ periodo: 'RANGO', desde, hasta })
            }
          }}
        >
          <label className="text-sm">
            <span className="mb-1 block font-medium text-slate-700">Desde</span>
            <input
              type="date"
              value={desde}
              onChange={(e) => setDesde(e.target.value)}
              className="h-11 rounded-xl border border-slate-300 bg-white px-3"
            />
          </label>
          <label className="text-sm">
            <span className="mb-1 block font-medium text-slate-700">Hasta</span>
            <input
              type="date"
              value={hasta}
              onChange={(e) => setHasta(e.target.value)}
              className="h-11 rounded-xl border border-slate-300 bg-white px-3"
            />
          </label>
          <Boton type="submit" variante="secundario" disabled={!desde || !hasta}>
            Ver
          </Boton>
        </form>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- indicadores

/**
 * Un número grande con su comparación. La flecha y el texto dicen si subió o bajó (no solo el color);
 * el color dice si eso es bueno: verde si sube y es bueno, rojo si baja y es malo; gris si es neutro.
 */
export function Indicador({
  titulo,
  valor,
  detalle,
  actual,
  anterior,
  nombreAnterior,
  subirEsBueno,
  nota,
}: {
  titulo: string
  valor: string
  detalle?: ReactNode
  actual: number
  anterior: number
  nombreAnterior: string
  /** true: subir es bueno (ventas, ganancia); undefined: neutro (compras) */
  subirEsBueno?: boolean
  nota?: ReactNode
}) {
  const cambio = variacion(actual, anterior)
  const color =
    cambio === null || cambio === 0 || subirEsBueno === undefined
      ? 'text-slate-600'
      : (cambio > 0) === subirEsBueno
        ? 'text-emerald-700'
        : 'text-red-700'
  return (
    <Tarjeta>
      <h2 className="text-sm font-medium text-slate-600">{titulo}</h2>
      <p className="mt-1 text-3xl font-bold leading-tight text-slate-900">{valor}</p>
      {detalle && <p className="mt-0.5 text-slate-600">{detalle}</p>}
      <p className={`mt-2 text-sm font-medium ${color}`}>
        {cambio === null
          ? `Sin datos ${nombreAnterior.replace(/^el /, 'del ')} para comparar`
          : `${cambio > 0 ? '▲' : cambio < 0 ? '▼' : '='} ${cambio > 0 ? '+' : ''}${cambio} % vs. ${nombreAnterior} (${dinero(anterior)})`}
      </p>
      {nota && <p className="mt-2 text-sm text-slate-500">{nota}</p>}
    </Tarjeta>
  )
}

// ---------------------------------------------------------------- gráfico

/** Tono más claro de la marca para los meses anteriores; el mes actual va en el color de la marca. */
const TONO_MESES_ANTERIORES = '#6f93b8'

/**
 * Ventas por mes (12 columnas). Tocar o pasar sobre una columna muestra sus cifras arriba;
 * todas las cifras también están en la tabla "Ver cifras".
 */
export function GraficoVentasMes({ meses }: { meses: Resumen['ventasPorMes'] }) {
  const ultimo = meses.length - 1
  const [activo, setActivo] = useState(ultimo)
  const maximo = Math.max(0, ...meses.map((m) => m.ventas))
  const elegido = meses[activo] ?? meses[ultimo]

  return (
    <Tarjeta>
      <h2 className="font-semibold">Ventas por mes</h2>
      <p className="mt-1 min-h-12 text-slate-600" aria-live="polite">
        <strong className="text-lg text-slate-900">{dinero(elegido.ventas)}</strong> en {mesLargo(elegido.mes)}
        <span className="block text-sm">Ganancia {dinero(elegido.ganancia)}</span>
      </p>

      <div className="relative mt-2">
        <span className="absolute left-0 top-0 text-[11px] text-slate-400">{dinero(maximo)}</span>
        <div className="flex h-44 items-end border-b border-slate-300 pt-5">
          {meses.map((m, i) => {
            const alto = maximo === 0 ? 0 : (m.ventas / maximo) * 100
            return (
              <button
                key={m.mes}
                type="button"
                aria-label={`${mesLargo(m.mes)}: ventas ${dinero(m.ventas)}, ganancia ${dinero(m.ganancia)}`}
                aria-pressed={i === activo}
                onMouseEnter={() => setActivo(i)}
                onFocus={() => setActivo(i)}
                onClick={() => setActivo(i)}
                className="group flex h-full flex-1 items-end justify-center rounded-t-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-marca/40"
              >
                <span
                  className={`block w-[70%] max-w-6 rounded-t-sm transition-opacity ${
                    i === activo ? 'opacity-100' : 'opacity-85 group-hover:opacity-100'
                  } ${i === activo ? 'outline-2 outline-offset-1 outline-marca/30' : ''}`}
                  style={{
                    height: `${alto}%`,
                    minHeight: m.ventas > 0 ? 2 : 0,
                    backgroundColor: i === ultimo ? 'var(--color-marca)' : TONO_MESES_ANTERIORES,
                  }}
                />
              </button>
            )
          })}
        </div>
        <div className="flex">
          {meses.map((m, i) => (
            <span
              key={m.mes}
              className={`flex-1 pt-1 text-center text-[11px] ${i === activo ? 'font-semibold text-slate-900' : 'text-slate-500'}`}
            >
              {mesCorto(m.mes)}
            </span>
          ))}
        </div>
      </div>

      <details className="mt-3">
        <summary className="cursor-pointer py-1 text-sm font-semibold text-marca">Ver cifras</summary>
        <table className="mt-2 w-full text-sm tabular-nums">
          <thead>
            <tr className="text-left text-slate-500">
              <th className="py-1 font-medium">Mes</th>
              <th className="py-1 text-right font-medium">Ventas</th>
              <th className="py-1 text-right font-medium">Ganancia</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {[...meses].reverse().map((m) => (
              <tr key={m.mes}>
                <td className="py-1.5">{mesLargo(m.mes)}</td>
                <td className="py-1.5 text-right">{dinero(m.ventas)}</td>
                <td className="py-1.5 text-right">{dinero(m.ganancia)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </Tarjeta>
  )
}
