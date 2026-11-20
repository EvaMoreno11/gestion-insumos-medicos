import { useState } from 'react'
import { Link } from 'react-router'
import { mensajeDe } from '../api/http'
import { movimientos } from '../api/recursos'
import type { EntradaReciente } from '../api/tipos'
import { Aviso, Boton, EstadoCarga, Vacio } from '../componentes/basicos'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { dinero, hora, mesAnio, nombreDelDia, unir } from '../util/formato'

/**
 * Registro de las últimas entradas (lo que llegó al almacén), agrupadas por día, las más recientes primero.
 * Solo para consultar: no incluye la carga inicial desde Excel.
 */
export default function UltimasEntradas() {
  const primera = useCarga(() => movimientos.ultimasEntradas(0))
  const [mas, setMas] = useState<EntradaReciente[]>([])
  const [pagina, setPagina] = useState(0)
  const [cargandoMas, setCargandoMas] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const lista = [...(primera.datos?.contenido ?? []), ...mas]
  const total = primera.datos?.totalElementos ?? 0

  async function verMas() {
    setCargandoMas(true)
    setError(null)
    try {
      const siguiente = await movimientos.ultimasEntradas(pagina + 1)
      setMas((anteriores) => [...anteriores, ...siguiente.contenido])
      setPagina(pagina + 1)
    } catch (e) {
      setError(mensajeDe(e))
    } finally {
      setCargandoMas(false)
    }
  }

  // Agrupar por día, conservando el orden (más recientes primero)
  const dias: { dia: string; entradas: EntradaReciente[] }[] = []
  for (const entrada of lista) {
    const dia = nombreDelDia(entrada.fecha)
    if (dias.at(-1)?.dia !== dia) {
      dias.push({ dia, entradas: [] })
    }
    dias.at(-1)?.entradas.push(entrada)
  }

  return (
    <Pantalla titulo="Últimas entradas" volver="/entrada" ancho="amplio">
      <EstadoCarga cargando={primera.cargando} error={primera.error} hayDatos={primera.datos !== undefined}>
        {lista.length === 0 && <Vacio>Todavía no se registró ninguna entrada.</Vacio>}

        <div className="space-y-5">
          {dias.map(({ dia, entradas }) => (
            <section key={dia}>
              <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-500">{dia}</h2>
              <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
                {entradas.map((e) => (
                  <li key={e.id}>
                    <FilaEntrada entrada={e} />
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>

        {error && (
          <div className="mt-3">
            <Aviso tipo="error">{error}</Aviso>
          </div>
        )}
        {lista.length < total && (
          <Boton variante="secundario" ancho className="mt-4" disabled={cargandoMas} onClick={() => void verMas()}>
            {cargandoMas ? 'Cargando…' : `Ver más (${total - lista.length} más)`}
          </Boton>
        )}
      </EstadoCarga>
    </Pantalla>
  )
}

function FilaEntrada({ entrada: e }: { entrada: EntradaReciente }) {
  const lote = unir(' · ', e.numeroLote && `Lote ${e.numeroLote}`, e.fechaVencimiento && `F.V. ${mesAnio(e.fechaVencimiento)}`)
  return (
    <Link
      to={`/inventario/${e.productoId}`}
      className="flex items-start gap-3 rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50"
    >
      <span className="w-12 shrink-0 pt-0.5 text-sm text-slate-500 tabular-nums">{hora(e.fecha)}</span>
      <span className="min-w-0 flex-1">
        <span className="block font-semibold leading-snug break-words">{e.productoNombre}</span>
        <span className="block text-sm break-words text-slate-500">{unir(' · ', e.productoPresentacion, e.productoMarca)}</span>
        {lote && <span className="block text-sm text-slate-600">{lote}</span>}
        {e.costoUnitario !== null && (
          <span className="block text-sm text-slate-600">
            {dinero(e.costoUnitario)} c/u · total {dinero(e.costoUnitario * e.cantidad)}
          </span>
        )}
        {e.nota && <span className="block text-sm break-words text-slate-500 italic">{e.nota}</span>}
      </span>
      <span className="shrink-0 text-xl font-bold text-emerald-700 tabular-nums">+{e.cantidad}</span>
    </Link>
  )
}
