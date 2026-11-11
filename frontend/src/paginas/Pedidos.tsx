import { Link, useSearchParams } from 'react-router'
import { pedidos } from '../api/recursos'
import type { EstadoPedido } from '../api/tipos'
import { BotonEnlace, EstadoCarga, Etiqueta, Vacio } from '../componentes/basicos'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { dinero, fechaCorta, unir } from '../util/formato'

const PESTANAS: { estado: EstadoPedido; titulo: string }[] = [
  { estado: 'BORRADOR', titulo: 'Pendientes' },
  { estado: 'DESPACHADO', titulo: 'Despachados' },
  { estado: 'ANULADO', titulo: 'Anulados' },
]

export default function Pedidos() {
  const [params, setParams] = useSearchParams()
  const estado = (params.get('estado') as EstadoPedido | null) ?? 'BORRADOR'
  const { datos, cargando, error } = useCarga(() => pedidos.listar(estado), estado)

  return (
    <Pantalla
      titulo="Pedidos"
      volver="/"
      pie={
        <BotonEnlace a="/pedidos/nuevo" ancho>
          + Nuevo pedido
        </BotonEnlace>
      }
    >
      <div className="mb-3 grid grid-cols-3 gap-1 rounded-xl bg-slate-200 p-1" role="tablist">
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

      <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
        {datos && datos.contenido.length === 0 && (
          <Vacio>
            {estado === 'BORRADOR' ? 'No hay pedidos pendientes.' : 'No hay pedidos en esta lista.'}
          </Vacio>
        )}
        <ul className="space-y-2">
          {datos?.contenido.map((p) => (
            <li key={p.id}>
              <Link
                to={`/pedidos/${p.id}`}
                className="block rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50"
              >
                <div className="flex items-start justify-between gap-3">
                  <span className="min-w-0">
                    <span className="block truncate font-semibold">{p.cliente}</span>
                    <span className="block truncate text-sm text-slate-500">
                      {unir(' · ', p.local, p.numero ? `N° ${p.numero}` : null, fechaCorta(p.despachadoEn ?? p.creadoEn))}
                    </span>
                  </span>
                  <span className="shrink-0 text-lg font-bold">{dinero(p.total)}</span>
                </div>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  <Etiqueta color="gris">
                    {p.cantidadItems} {p.cantidadItems === 1 ? 'ítem' : 'ítems'}
                  </Etiqueta>
                  {p.estado === 'BORRADOR' &&
                    (p.itemsConFaltante > 0 ? (
                      <Etiqueta color="ambar">Faltan unidades en {p.itemsConFaltante}</Etiqueta>
                    ) : (
                      p.cantidadItems > 0 && <Etiqueta color="verde">Completo</Etiqueta>
                    ))}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </EstadoCarga>
    </Pantalla>
  )
}
