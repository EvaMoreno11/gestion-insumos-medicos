import { Link } from 'react-router'
import { pedidos } from '../api/recursos'
import { BotonEnlace, EstadoCarga, Tarjeta, Vacio } from '../componentes/basicos'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { unir } from '../util/formato'

/** Lista universal de lo que falta comprar para completar los pedidos pendientes. */
export default function PorComprar() {
  const { datos, cargando, error } = useCarga(() => pedidos.porComprar())
  const vacio = datos && datos.productos.length === 0 && datos.noRegistrados.length === 0

  return (
    <Pantalla titulo="Por comprar" volver="/">
      <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
        {vacio && <Vacio>No falta nada: todos los pedidos pendientes están completos.</Vacio>}

        <ul className="space-y-2">
          {datos?.productos.map((p) => (
            <li key={p.productoId}>
              <Tarjeta>
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h2 className="font-semibold leading-snug">{p.nombre}</h2>
                    <p className="text-sm text-slate-500">{unir(' · ', p.presentacion, p.marca)}</p>
                    <p className="mt-1 text-sm text-slate-600">
                      Piden {p.pedido} · hay {p.stockDisponible}
                    </p>
                  </div>
                  <div className="shrink-0 text-right">
                    <div className="text-xs uppercase tracking-wide text-slate-500">Faltan</div>
                    <div className="text-3xl font-bold leading-none text-red-700">{p.faltante}</div>
                  </div>
                </div>

                <ul className="mt-3 space-y-1 border-t border-slate-100 pt-3 text-sm">
                  {p.pedidos.map((e) => (
                    <li key={e.pedidoId}>
                      <Link to={`/pedidos/${e.pedidoId}`} className="flex justify-between gap-3 text-marca">
                        <span className="truncate underline-offset-2">{unir(' · ', e.cliente, e.local)}</span>
                        <span className="shrink-0 text-slate-600">le faltan {e.faltante}</span>
                      </Link>
                    </li>
                  ))}
                </ul>

                <div className="mt-3">
                  <BotonEnlace a={`/entrada?producto=${p.productoId}`} variante="secundario" ancho>
                    Llegó: registrar entrada
                  </BotonEnlace>
                </div>
              </Tarjeta>
            </li>
          ))}
        </ul>

        {datos && datos.noRegistrados.length > 0 && (
          <section className="mt-6">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">No registrados</h2>
            <p className="mb-2 text-sm text-slate-500">
              Ítems escritos a mano en un pedido. Hay que conseguirlos y, después, registrarlos como productos.
            </p>
            <ul className="space-y-2">
              {datos.noRegistrados.map((n, i) => (
                <li key={i}>
                  <Link
                    to={`/pedidos/${n.pedidoId}`}
                    className="flex items-start justify-between gap-3 rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50"
                  >
                    <span className="min-w-0">
                      <span className="block font-medium leading-snug">{n.descripcion}</span>
                      <span className="block text-sm text-slate-500">{unir(' · ', n.presentacion, n.marca)}</span>
                      <span className="block text-sm text-slate-500">{unir(' · ', n.cliente, n.local)}</span>
                    </span>
                    <span className="shrink-0 text-xl font-bold">{n.cantidad}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        )}
      </EstadoCarga>
    </Pantalla>
  )
}
