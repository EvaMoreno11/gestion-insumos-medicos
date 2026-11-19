import { Link, useSearchParams } from 'react-router'
import { cobranzas } from '../api/recursos'
import type { Deuda } from '../api/tipos'
import { EstadoCarga, Tarjeta, Vacio } from '../componentes/basicos'
import { EtiquetaCobro } from '../componentes/cobro'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { dinero, fechaDia, textoPlazo, unir } from '../util/formato'

type Pestana = 'vencidos' | 'por-cobrar' | 'sin-plazo' | 'a-favor'

/**
 * Lo que se debe de los pedidos despachados. Desde aquí se entra a cada nota para anotar
 * el plazo (contado o crédito) y los pagos.
 */
export default function Cobranzas() {
  const [params, setParams] = useSearchParams()
  const { datos, cargando, error } = useCarga(() => cobranzas.obtener())

  // Sin elegir pestaña, se abre la primera que tenga algo: vencidos, sin plazo o por cobrar
  const elegida = params.get('ver') as Pestana | null
  const pestana: Pestana =
    elegida ?? (!datos || datos.vencidos.length > 0 ? 'vencidos' : datos.sinPlazo.length > 0 ? 'sin-plazo' : 'por-cobrar')

  const pestanas: { valor: Pestana; titulo: string; lista: Deuda[] | undefined; vacio: string; ayuda: string }[] = [
    {
      valor: 'vencidos',
      titulo: 'Vencidos',
      lista: datos?.vencidos,
      vacio: 'Ningún cobro está vencido.',
      ayuda: 'Pasó el plazo y aún deben. Primero los más atrasados.',
    },
    {
      valor: 'por-cobrar',
      titulo: 'Por cobrar',
      lista: datos?.porCobrar,
      vacio: 'No hay cobros con plazo vigente.',
      ayuda: 'Tienen plazo y todavía no vence. Primero los que vencen antes.',
    },
    {
      valor: 'sin-plazo',
      titulo: 'Sin plazo',
      lista: datos?.sinPlazo,
      vacio: 'Todas las notas tienen su plazo anotado.',
      ayuda: 'Se despacharon y nadie anotó todavía cómo pagan. Si pagaron al contado, entra y toca “Pagó todo”.',
    },
    // Solo aparece si algún cliente pagó de más (por ejemplo, tras una devolución)
    ...(datos && datos.aFavor.length > 0
      ? [
          {
            valor: 'a-favor' as Pestana,
            titulo: 'A favor',
            lista: datos.aFavor,
            vacio: 'Ningún cliente tiene saldo a favor.',
            ayuda: `Pagaron más que el total corregido (${dinero(datos.totalAFavor)} en total): devuélvanles la diferencia o descuéntenla de su próximo pedido.`,
          },
        ]
      : []),
  ]
  const actual = pestanas.find((p) => p.valor === pestana) ?? pestanas[0]

  return (
    <Pantalla titulo="Cobranzas" volver="/" ancho="amplio">
      <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
        {datos && (
          <div className="space-y-3">
            <Tarjeta>
              <p className="text-sm font-medium text-slate-600">Te deben</p>
              <p className="text-3xl font-bold leading-tight">{dinero(datos.totalPorCobrar)}</p>
              <p className={`mt-1 font-medium ${datos.totalVencido > 0 ? 'text-red-700' : 'text-slate-600'}`}>
                {datos.totalVencido > 0
                  ? `${dinero(datos.totalVencido)} vencido · ${datos.clientesVencidos} ${datos.clientesVencidos === 1 ? 'cliente' : 'clientes'}`
                  : 'Nada vencido'}
              </p>
            </Tarjeta>

            <div
              className={`grid gap-1 rounded-xl bg-slate-200 p-1 lg:max-w-xl ${pestanas.length > 3 ? 'grid-cols-4' : 'grid-cols-3'}`}
              role="tablist"
            >
              {pestanas.map((p) => (
                <button
                  key={p.valor}
                  type="button"
                  role="tab"
                  aria-selected={pestana === p.valor}
                  onClick={() => setParams({ ver: p.valor }, { replace: true })}
                  className={`min-h-10 rounded-lg px-1 text-sm font-semibold ${
                    pestana === p.valor ? 'bg-white text-marca shadow-sm' : 'text-slate-600'
                  }`}
                >
                  {p.titulo}
                  {p.lista && p.lista.length > 0 && ` (${p.lista.length})`}
                </button>
              ))}
            </div>

            <p className="text-sm text-slate-600">{actual.ayuda}</p>
            {actual.lista?.length === 0 && <Vacio>{actual.vacio}</Vacio>}
            <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
              {actual.lista?.map((d) => (
                <li key={d.pedidoId}>
                  <FilaDeuda deuda={d} />
                </li>
              ))}
            </ul>
          </div>
        )}
      </EstadoCarga>
    </Pantalla>
  )
}

function FilaDeuda({ deuda }: { deuda: Deuda }) {
  return (
    <Link
      to={`/pedidos/${deuda.pedidoId}?desde=cobranzas`}
      className="block rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50"
    >
      <div className="flex items-start justify-between gap-3">
        <span className="min-w-0">
          <span className="block truncate font-semibold">{deuda.cliente}</span>
          <span className="block truncate text-sm text-slate-500">
            {unir(
              ' · ',
              deuda.local,
              deuda.numero ? `N° ${deuda.numero}` : 'Solo tabla',
              `despachado el ${fechaDia(deuda.despachado)}`,
            )}
          </span>
        </span>
        <span className="shrink-0 text-right">
          <span className={`block text-lg font-bold ${deuda.estado === 'VENCIDO' ? 'text-red-700' : ''}`}>
            {dinero(Math.abs(deuda.saldo))}
          </span>
          {deuda.pagado > 0 && <span className="block text-xs text-slate-500">de {dinero(deuda.total)}</span>}
        </span>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-1.5 text-sm text-slate-600">
        <EtiquetaCobro estado={deuda.estado} dias={deuda.dias} />
        {deuda.plazoDias !== null && (
          <span>
            {textoPlazo(deuda.plazoDias)} · vence el {fechaDia(deuda.vence)}
          </span>
        )}
      </div>
    </Link>
  )
}
