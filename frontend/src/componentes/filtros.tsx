import { useState } from 'react'
import type { BusquedaConFechas } from '../hooks'
import { fechaDia } from '../util/formato'
import { Boton, Buscador, Campo, ESTILO_CAMPO } from './basicos'

/**
 * Buscador y "📅 Elegir fechas" de una lista por días (despachados, entradas registradas). Con fechas elegidas, el
 * botón muestra el rango y una × para quitarlas. En la PC van en una sola fila.
 */
export function BuscadorYFechas({ filtro, placeholder }: { filtro: BusquedaConFechas; placeholder: string }) {
  const [eligiendo, setEligiendo] = useState(false)
  const { elegidas } = filtro

  return (
    <>
      <div className="space-y-3 lg:grid lg:grid-cols-[minmax(0,1fr)_20rem] lg:gap-3 lg:space-y-0">
        <Buscador valor={filtro.texto} onCambio={filtro.setTexto} placeholder={placeholder} />
        {elegidas ? (
          <div className="flex gap-2">
            <button
              type="button"
              className={`${ESTILO_CAMPO} min-w-0 flex-1 text-left font-medium`}
              onClick={() => setEligiendo(!eligiendo)}
            >
              📅{' '}
              {elegidas.desde === elegidas.hasta
                ? fechaDia(elegidas.desde)
                : `${fechaDia(elegidas.desde)} – ${fechaDia(elegidas.hasta)}`}
            </button>
            <button
              type="button"
              aria-label="Quitar fechas"
              className="min-h-12 w-12 shrink-0 rounded-xl border border-slate-300 bg-white text-xl text-slate-600 active:bg-slate-100"
              onClick={() => {
                filtro.cambiarFechas(null)
                setEligiendo(false)
              }}
            >
              ×
            </button>
          </div>
        ) : (
          <button
            type="button"
            className={`${ESTILO_CAMPO} text-left font-medium text-marca`}
            aria-expanded={eligiendo}
            onClick={() => setEligiendo(!eligiendo)}
          >
            📅 Elegir fechas
          </button>
        )}
      </div>

      {eligiendo && (
        <ElegirFechas
          desde={elegidas?.desde ?? ''}
          hasta={elegidas?.hasta ?? ''}
          hoy={filtro.hoy}
          onAplicar={(fechas) => {
            filtro.cambiarFechas(fechas)
            setEligiendo(false)
          }}
          onCancelar={() => setEligiendo(false)}
        />
      )}
    </>
  )
}

/** Desde y Hasta con el calendario del celular. Para un solo día, la misma fecha en los dos (o Hasta vacío). */
function ElegirFechas({
  desde: desdeInicial,
  hasta: hastaInicial,
  hoy,
  onAplicar,
  onCancelar,
}: {
  desde: string
  hasta: string
  hoy: string
  onAplicar: (fechas: { desde: string; hasta: string }) => void
  onCancelar: () => void
}) {
  const [desde, setDesde] = useState(desdeInicial)
  const [hasta, setHasta] = useState(hastaInicial)
  const hastaFinal = hasta || desde
  const alReves = desde !== '' && hastaFinal < desde

  return (
    <div className="space-y-3 rounded-2xl border border-slate-200 bg-white p-3 lg:max-w-xl">
      <div className="grid grid-cols-2 gap-3">
        <Campo etiqueta="Desde" type="date" value={desde} max={hoy} onChange={(e) => setDesde(e.target.value)} />
        <Campo
          etiqueta="Hasta"
          type="date"
          value={hasta}
          min={desde || undefined}
          max={hoy}
          onChange={(e) => setHasta(e.target.value)}
          error={alReves ? 'Debe ser igual o posterior a «Desde»' : undefined}
          ayuda="Vacío: solo ese día"
        />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Boton variante="secundario" onClick={onCancelar}>
          Cancelar
        </Boton>
        <Boton disabled={desde === '' || alReves} onClick={() => onAplicar({ desde, hasta: hastaFinal })}>
          Ver esas fechas
        </Boton>
      </div>
    </div>
  )
}
