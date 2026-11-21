import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { pedidos } from '../api/recursos'
import type { EstadoPedido, PedidoResumen } from '../api/tipos'
import { Aviso, Boton, BotonEnlace, Buscador, Campo, ESTILO_CAMPO, EstadoCarga, Etiqueta, Vacio } from '../componentes/basicos'
import { CostoYGanancia } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga, useRetardo } from '../hooks'
import { diaLocal, dinero, fechaCorta, fechaDia, nombreDeFecha, sumarDias, unir } from '../util/formato'

const PESTANAS: { estado: EstadoPedido; titulo: string }[] = [
  { estado: 'BORRADOR', titulo: 'Pendientes' },
  { estado: 'DESPACHADO', titulo: 'Despachados' },
  { estado: 'ANULADO', titulo: 'Anulados' },
]

export default function Pedidos() {
  const [params, setParams] = useSearchParams()
  const estado = (params.get('estado') as EstadoPedido | null) ?? 'BORRADOR'

  return (
    <Pantalla
      titulo="Pedidos"
      volver="/"
      ancho="amplio"
      pie={
        <BotonEnlace a="/pedidos/nuevo" ancho>
          + Nuevo pedido
        </BotonEnlace>
      }
    >
      <div className="mb-3 grid grid-cols-3 gap-1 rounded-xl bg-slate-200 p-1 lg:max-w-xl" role="tablist">
        {PESTANAS.map((p) => (
          <button
            key={p.estado}
            type="button"
            role="tab"
            aria-selected={estado === p.estado}
            onClick={() => setParams({ estado: p.estado }, { replace: true })}
            className={`min-h-10 rounded-lg text-sm font-semibold ${
              estado === p.estado ? 'bg-white text-marca shadow-sm' : 'text-slate-600'
            }`}
          >
            {p.titulo}
          </button>
        ))}
      </div>

      {estado === 'DESPACHADO' ? <Despachados /> : <ListaSimple key={estado} estado={estado} />}
    </Pantalla>
  )
}

/** Pendientes y Anulados: los más recientes primero, como siempre. */
function ListaSimple({ estado }: { estado: EstadoPedido }) {
  const { datos, cargando, error } = useCarga(() => pedidos.listar(estado), estado)
  return (
    <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
      {datos && datos.contenido.length === 0 && (
        <Vacio>{estado === 'BORRADOR' ? 'No hay pedidos pendientes.' : 'No hay pedidos en esta lista.'}</Vacio>
      )}
      <ListaDePedidos pedidos={datos?.contenido ?? []} />
    </EstadoCarga>
  )
}

function ListaDePedidos({ pedidos: lista }: { pedidos: PedidoResumen[] }) {
  return (
    <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
      {lista.map((p) => (
        <li key={p.id}>
          <TarjetaPedido pedido={p} />
        </li>
      ))}
    </ul>
  )
}

function TarjetaPedido({ pedido: p }: { pedido: PedidoResumen }) {
  return (
    <Link to={`/pedidos/${p.id}`} className="block rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50">
      <div className="flex items-start justify-between gap-3">
        <span className="min-w-0">
          <span className="block font-semibold leading-snug break-words">{p.cliente}</span>
          <span className="block text-sm break-words text-slate-500">
            {unir(' · ', p.local, p.numero ? `N° ${p.numero}` : null, fechaCorta(p.despachadoEn ?? p.creadoEn))}
          </span>
          {p.nota && <span className="block truncate text-sm text-slate-600">{p.nota}</span>}
        </span>
        <span className="shrink-0 text-right">
          <span className="block text-lg font-bold">{dinero(p.total)}</span>
          <CostoYGanancia costo={p.costo} className="mt-0.5" />
        </span>
      </div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        <Etiqueta color="gris">
          {p.cantidadItems} {p.cantidadItems === 1 ? 'ítem' : 'ítems'}
        </Etiqueta>
        {p.documento === 'SOLO_TABLA' && <Etiqueta color="azul">Solo tabla</Etiqueta>}
        {p.origenId !== null && <Etiqueta color="azul">{p.origenNumero ? `Saldo de ${p.origenNumero}` : 'Saldo'}</Etiqueta>}
        {p.estado === 'BORRADOR' &&
          (p.itemsConFaltante > 0 ? (
            <Etiqueta color="ambar">Faltan unidades en {p.itemsConFaltante}</Etiqueta>
          ) : (
            p.cantidadItems > 0 && <Etiqueta color="verde">Completo</Etiqueta>
          ))}
      </div>
    </Link>
  )
}

/** Pedidos cargados con "Ver más" para una búsqueda concreta. */
interface Extra {
  clave: string
  pedidos: PedidoResumen[]
  pagina: number
}

/**
 * Despachados, del más reciente al más antiguo según el día de despacho y separados por día. Sin filtros se ven
 * los últimos 7 días; al buscar un cliente o un N° de nota sin elegir fechas, se busca en todo el historial.
 * La búsqueda y las fechas viven en la URL: al volver de un pedido, la lista sigue igual.
 */
function Despachados() {
  const [params, setParams] = useSearchParams()
  const [texto, setTexto] = useState(params.get('buscar') ?? '')
  const buscado = useRetardo(texto.trim())
  const desdeElegido = params.get('desde')
  const hastaElegido = params.get('hasta')
  const conFechas = desdeElegido !== null && hastaElegido !== null
  const [eligiendo, setEligiendo] = useState(false)

  const hoy = diaLocal()
  const desde = conFechas ? desdeElegido : buscado ? null : sumarDias(hoy, -6)
  const hasta = conFechas ? hastaElegido : buscado ? null : hoy
  const clave = `${buscado}|${desde ?? ''}|${hasta ?? ''}`
  const primera = useCarga(() => pedidos.despachados({ buscar: buscado, desde, hasta }), clave)

  const [extra, setExtra] = useState<Extra>()
  const [cargandoMas, setCargandoMas] = useState(false)
  const [errorMas, setErrorMas] = useState<string | null>(null)

  // Refleja la búsqueda en la URL
  useEffect(() => {
    setParams(
      (actual) => {
        const nuevos = new URLSearchParams(actual)
        if (buscado) {
          nuevos.set('buscar', buscado)
        } else {
          nuevos.delete('buscar')
        }
        return nuevos
      },
      { replace: true },
    )
  }, [buscado, setParams])

  function cambiarFechas(fechas: { desde: string; hasta: string } | null) {
    setParams(
      (actual) => {
        const nuevos = new URLSearchParams(actual)
        if (fechas) {
          nuevos.set('desde', fechas.desde)
          nuevos.set('hasta', fechas.hasta)
        } else {
          nuevos.delete('desde')
          nuevos.delete('hasta')
        }
        return nuevos
      },
      { replace: true },
    )
    setEligiendo(false)
  }

  // Lo cargado con "Ver más" solo vale para la búsqueda en la que se pidió
  const mas = extra?.clave === clave && !primera.cargando ? extra : undefined
  const lista = [...(primera.datos?.lista.contenido ?? []), ...(mas?.pedidos ?? [])]
  const datos = primera.datos

  async function verMas() {
    setCargandoMas(true)
    setErrorMas(null)
    try {
      const pagina = (mas?.pagina ?? 0) + 1
      const r = await pedidos.despachados({ buscar: buscado, desde, hasta, pagina })
      setExtra({ clave, pedidos: [...(mas?.pedidos ?? []), ...r.lista.contenido], pagina })
    } catch (e) {
      setErrorMas(mensajeDe(e))
    } finally {
      setCargandoMas(false)
    }
  }

  // Agrupar por día de despacho, conservando el orden (más recientes primero)
  const dias: { fecha: string; pedidos: PedidoResumen[] }[] = []
  for (const p of lista) {
    const fecha = diaLocal(new Date(p.despachadoEn ?? p.creadoEn))
    if (dias.at(-1)?.fecha !== fecha) {
      dias.push({ fecha, pedidos: [] })
    }
    dias.at(-1)?.pedidos.push(p)
  }

  const periodo = conFechas
    ? desdeElegido === hastaElegido
      ? `El ${fechaDia(desdeElegido)}`
      : `Del ${fechaDia(desdeElegido)} al ${fechaDia(hastaElegido)}`
    : buscado
      ? 'Todo el historial'
      : 'Últimos 7 días'

  return (
    <div className="space-y-3">
      <div className="space-y-3 lg:grid lg:grid-cols-[minmax(0,1fr)_20rem] lg:gap-3 lg:space-y-0">
        <Buscador valor={texto} onCambio={setTexto} placeholder="Buscar cliente o N° de nota…" />
        {conFechas ? (
          <div className="flex gap-2">
            <button
              type="button"
              className={`${ESTILO_CAMPO} min-w-0 flex-1 text-left font-medium`}
              onClick={() => setEligiendo(!eligiendo)}
            >
              📅 {desdeElegido === hastaElegido ? fechaDia(desdeElegido) : `${fechaDia(desdeElegido)} – ${fechaDia(hastaElegido)}`}
            </button>
            <button
              type="button"
              aria-label="Quitar fechas"
              className="min-h-12 w-12 shrink-0 rounded-xl border border-slate-300 bg-white text-xl text-slate-600 active:bg-slate-100"
              onClick={() => cambiarFechas(null)}
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
          desde={desdeElegido ?? ''}
          hasta={hastaElegido ?? ''}
          hoy={hoy}
          onAplicar={cambiarFechas}
          onCancelar={() => setEligiendo(false)}
        />
      )}

      <EstadoCarga cargando={primera.cargando} error={primera.error} hayDatos={datos !== undefined}>
        {datos && (
          <p className="text-sm text-slate-600" role="status">
            <strong>{periodo}</strong>
            {buscado && ` · «${buscado}»`} · {datos.pedidos} {datos.pedidos === 1 ? 'pedido' : 'pedidos'} ·{' '}
            {dinero(datos.total)}
          </p>
        )}
        {datos && datos.pedidos === 0 && (
          <Vacio>
            {buscado
              ? 'No se encontró ningún pedido despachado con esa búsqueda.'
              : conFechas
                ? 'No hay pedidos despachados en esas fechas.'
                : 'No hay pedidos despachados en los últimos 7 días.'}
          </Vacio>
        )}

        <div className="space-y-5">
          {dias.map(({ fecha, pedidos: delDia }) => {
            const resumen = datos?.dias.find((d) => d.fecha === fecha)
            return (
              <section key={fecha}>
                <h2 className="mb-2 text-sm font-semibold text-slate-600">
                  <span className="uppercase tracking-wide text-slate-500">{nombreDeFecha(fecha)}</span>
                  {resumen &&
                    ` · ${resumen.pedidos} ${resumen.pedidos === 1 ? 'pedido' : 'pedidos'} · ${dinero(resumen.total)}`}
                </h2>
                <ListaDePedidos pedidos={delDia} />
              </section>
            )
          })}
        </div>

        {errorMas && <Aviso tipo="error">{errorMas}</Aviso>}
        {datos && lista.length < datos.pedidos && (
          <Boton variante="secundario" ancho disabled={cargandoMas} onClick={() => void verMas()}>
            {cargandoMas ? 'Cargando…' : `Ver más (${datos.pedidos - lista.length} más)`}
          </Boton>
        )}
      </EstadoCarga>
    </div>
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
