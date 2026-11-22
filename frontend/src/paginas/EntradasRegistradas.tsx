import { Link } from 'react-router'
import { movimientos } from '../api/recursos'
import type { EntradaRegistrada } from '../api/tipos'
import { Aviso, Boton, EstadoCarga, Vacio } from '../componentes/basicos'
import { BuscadorYFechas } from '../componentes/filtros'
import { useBusquedaConFechas, useListaConVerMas } from '../hooks'
import { diaLocal, dinero, hora, mesAnio, nombreDeFecha, unir } from '../util/formato'

/**
 * Pestaña "Entradas registradas" de Registrar entrada: lo que llegó al almacén, de lo más reciente a lo más
 * antiguo y separado por día, con lo que suma cada día. Sin filtros se ven los últimos 7 días; al buscar un
 * producto o un N° de lote sin elegir fechas, todo el historial. No incluye la carga inicial desde Excel.
 */
export default function EntradasRegistradas() {
  const filtro = useBusquedaConFechas()
  const { buscado, desde, hasta } = filtro
  const { datos, cargando, error, lista, verMas, cargandoMas, errorMas } = useListaConVerMas(
    (pagina) => movimientos.entradas({ buscar: buscado, desde, hasta, pagina }),
    filtro.clave,
    (r) => r.lista.contenido,
  )

  // Agrupar por día, conservando el orden (más recientes primero)
  const dias: { fecha: string; entradas: EntradaRegistrada[] }[] = []
  for (const e of lista) {
    const fecha = diaLocal(new Date(e.fecha))
    if (dias.at(-1)?.fecha !== fecha) {
      dias.push({ fecha, entradas: [] })
    }
    dias.at(-1)?.entradas.push(e)
  }

  return (
    <div className="space-y-3">
      <BuscadorYFechas filtro={filtro} placeholder="Buscar producto o N° de lote…" />

      <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
        {datos && (
          <p className="text-sm text-slate-600" role="status">
            <strong>{filtro.periodo}</strong>
            {buscado && ` · «${buscado}»`} · {textoEntradas(datos.entradas)} · {dinero(datos.total)}
            {datos.sinCosto > 0 && <span className="text-amber-700"> · {datos.sinCosto} sin costo</span>}
          </p>
        )}
        {datos && datos.entradas === 0 && (
          <Vacio>
            {buscado
              ? 'No se encontró ninguna entrada con esa búsqueda.'
              : filtro.elegidas
                ? 'No hay entradas registradas en esas fechas.'
                : 'No hay entradas registradas en los últimos 7 días.'}
          </Vacio>
        )}

        <div className="space-y-5">
          {dias.map(({ fecha, entradas }) => {
            const resumen = datos?.dias.find((d) => d.fecha === fecha)
            return (
              <section key={fecha}>
                <h2 className="mb-2 text-sm font-semibold text-slate-600">
                  <span className="uppercase tracking-wide text-slate-500">{nombreDeFecha(fecha)}</span>
                  {resumen && ` · ${textoEntradas(resumen.entradas)} · ${dinero(resumen.total)}`}
                  {resumen && resumen.sinCosto > 0 && (
                    <span className="text-amber-700"> · {resumen.sinCosto} sin costo</span>
                  )}
                </h2>
                <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
                  {entradas.map((e) => (
                    <li key={e.id}>
                      <FilaEntrada entrada={e} />
                    </li>
                  ))}
                </ul>
              </section>
            )
          })}
        </div>

        {errorMas && <Aviso tipo="error">{errorMas}</Aviso>}
        {datos && lista.length < datos.entradas && (
          <Boton variante="secundario" ancho disabled={cargandoMas} onClick={() => void verMas()}>
            {cargandoMas ? 'Cargando…' : `Ver más (${datos.entradas - lista.length} más)`}
          </Boton>
        )}
      </EstadoCarga>
    </div>
  )
}

function textoEntradas(n: number): string {
  return `${n} ${n === 1 ? 'entrada' : 'entradas'}`
}

/** Una entrada: hora, producto, lote, costo y cuánto entró. Lleva al producto. */
function FilaEntrada({ entrada: e }: { entrada: EntradaRegistrada }) {
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
        {e.costoUnitario !== null ? (
          <span className="block text-sm text-slate-600">
            {dinero(e.costoUnitario)} c/u · total {dinero(e.costoUnitario * e.cantidad)}
          </span>
        ) : (
          <span className="block text-sm text-amber-700">Sin costo</span>
        )}
        {e.nota && <span className="block text-sm break-words text-slate-500 italic">{e.nota}</span>}
      </span>
      <span className="shrink-0 text-xl font-bold text-emerald-700 tabular-nums">+{e.cantidad}</span>
    </Link>
  )
}
