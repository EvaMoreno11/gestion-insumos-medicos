import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { resumen } from '../api/recursos'
import { EstadoCarga, Tarjeta, Vacio } from '../componentes/basicos'
import { Pantalla } from '../componentes/Pantalla'
import { SelectorPeriodo } from '../componentes/resumen'
import { useCarga, usePeriodo } from '../hooks'
import { dinero, fechaDia, rangoFechas, unir } from '../util/formato'

/** Segunda pantalla del resumen: ventas por producto y cliente, inventario y pedidos pendientes. */
export default function MasDetalles() {
  const [consulta, setConsulta, textoParams] = usePeriodo()
  const { datos, cargando, error } = useCarga(() => resumen.detalles(consulta), textoParams)

  return (
    <Pantalla titulo="Más detalles" volver={`/resumen?${textoParams}`} ancho="amplio">
      <div className="space-y-3">
        <SelectorPeriodo key={textoParams} consulta={consulta} onCambio={setConsulta} />
        <EstadoCarga cargando={cargando} error={error} hayDatos={datos !== undefined}>
          {datos && (
            <div className={`space-y-6 ${cargando ? 'opacity-60' : ''}`}>
              {/* ---------------------------------------------------------------- ventas */}
              <Seccion titulo="Ventas" detalle={rangoFechas(datos.periodo.desde, datos.periodo.hasta)}>
                <Tarjeta>
                  <h3 className="font-semibold">Productos más vendidos</h3>
                  {datos.productosMasVendidos.length === 0 ? (
                    <Vacio>No hubo ventas en este periodo.</Vacio>
                  ) : (
                    <ol className="mt-2 divide-y divide-slate-100">
                      {datos.productosMasVendidos.map((p, i) => (
                        <li key={p.productoId}>
                          <Link to={`/inventario/${p.productoId}`} className="flex items-start gap-3 py-2">
                            <span className="w-5 shrink-0 text-slate-400">{i + 1}</span>
                            <span className="min-w-0 flex-1">
                              <span className="block font-medium leading-snug">{p.nombre}</span>
                              <span className="block text-sm text-slate-500">
                                {unir(' · ', p.presentacion, p.marca)} · {p.unidades} vendidas
                              </span>
                            </span>
                            <span className="shrink-0 font-semibold tabular-nums">{dinero(p.ventas)}</span>
                          </Link>
                        </li>
                      ))}
                    </ol>
                  )}
                </Tarjeta>

                <Tarjeta>
                  <h3 className="font-semibold">Mejores clientes</h3>
                  {datos.mejoresClientes.length === 0 ? (
                    <Vacio>No hubo ventas en este periodo.</Vacio>
                  ) : (
                    <ol className="mt-2 divide-y divide-slate-100">
                      {datos.mejoresClientes.map((c, i) => (
                        <li key={c.clienteId}>
                          <Link to={`/clientes/${c.clienteId}`} className="flex items-start gap-3 py-2">
                            <span className="w-5 shrink-0 text-slate-400">{i + 1}</span>
                            <span className="min-w-0 flex-1">
                              <span className="block font-medium leading-snug">{c.nombre}</span>
                              <span className="block text-sm text-slate-500">
                                {c.pedidos} {c.pedidos === 1 ? 'pedido' : 'pedidos'}
                              </span>
                            </span>
                            <span className="shrink-0 font-semibold tabular-nums">{dinero(c.ventas)}</span>
                          </Link>
                        </li>
                      ))}
                    </ol>
                  )}
                </Tarjeta>

                <Tarjeta>
                  <h3 className="font-semibold">Clientes que dejaron de comprar</h3>
                  <p className="text-sm text-slate-500">
                    Sin pedidos hace más de {datos.diasInactivo} días. Puede ser buen momento para llamarlos.
                  </p>
                  {datos.clientesInactivos.length === 0 ? (
                    <Vacio>Todos los clientes compraron en los últimos {datos.diasInactivo} días.</Vacio>
                  ) : (
                    <ul className="mt-2 divide-y divide-slate-100">
                      {datos.clientesInactivos.map((c) => (
                        <li key={c.clienteId}>
                          <Link to={`/clientes/${c.clienteId}`} className="flex items-start justify-between gap-3 py-2">
                            <span className="min-w-0">
                              <span className="block font-medium leading-snug">{c.nombre}</span>
                              <span className="block text-sm text-slate-500">
                                Último pedido {fechaDia(c.ultimoPedido)} · hace {c.diasSinComprar} días
                              </span>
                            </span>
                            <span className="shrink-0 text-right text-sm text-slate-600">
                              <span className="block font-semibold tabular-nums text-slate-900">
                                {dinero(c.totalHistorico)}
                              </span>
                              en total
                            </span>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </Tarjeta>
              </Seccion>

              {/* ---------------------------------------------------------------- inventario */}
              <Seccion titulo="Inventario" detalle="Al día de hoy">
                <div className="grid grid-cols-2 gap-3 lg:col-span-full lg:grid-cols-2">
                  <Tarjeta>
                    <h3 className="text-sm font-medium text-slate-600">Valor del inventario</h3>
                    <p className="mt-1 text-2xl font-bold">{dinero(datos.inventario.valor)}</p>
                    <p className="text-sm text-slate-600">{datos.inventario.unidades} unidades en stock</p>
                    {datos.inventario.productosSinCosto > 0 && (
                      <p className="mt-1 text-sm text-slate-500">
                        {datos.inventario.productosSinCosto}{' '}
                        {datos.inventario.productosSinCosto === 1 ? 'producto sin costo no suma' : 'productos sin costo no suman'}.
                      </p>
                    )}
                  </Tarjeta>
                  <Link to="/alertas?ver=vencidos" className="block">
                    <Tarjeta className="h-full">
                      <h3 className="text-sm font-medium text-slate-600">Vencido sin vender</h3>
                      <p className={`mt-1 text-2xl font-bold ${datos.inventario.valorVencido > 0 ? 'text-red-700' : ''}`}>
                        {dinero(datos.inventario.valorVencido)}
                      </p>
                      <p className="text-sm text-slate-600">
                        {datos.inventario.lotesVencidos} {datos.inventario.lotesVencidos === 1 ? 'lote vencido' : 'lotes vencidos'}
                      </p>
                    </Tarjeta>
                  </Link>
                </div>

                <Tarjeta>
                  <h3 className="font-semibold">Productos sin movimiento</h3>
                  <p className="text-sm text-slate-500">
                    Con stock y sin ventas en los últimos {datos.diasSinMovimiento} días.
                  </p>
                  {datos.sinMovimiento.length === 0 ? (
                    <Vacio>Todo lo que hay en stock se vendió en los últimos {datos.diasSinMovimiento} días.</Vacio>
                  ) : (
                    <ul className="mt-2 divide-y divide-slate-100">
                      {datos.sinMovimiento.map((p) => (
                        <li key={p.productoId}>
                          <Link to={`/inventario/${p.productoId}`} className="flex items-start justify-between gap-3 py-2">
                            <span className="min-w-0">
                              <span className="block font-medium leading-snug">{p.nombre}</span>
                              <span className="block text-sm text-slate-500">
                                {p.stock} en stock ·{' '}
                                {p.ultimaSalida ? `última venta ${fechaDia(p.ultimaSalida)}` : 'nunca se vendió'}
                              </span>
                            </span>
                            <span className="shrink-0 font-semibold tabular-nums">{dinero(p.valor)}</span>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </Tarjeta>
              </Seccion>

              {/* ---------------------------------------------------------------- pedidos */}
              <Seccion titulo="Pedidos" detalle="Al día de hoy">
                <Link to="/pedidos" className="block">
                  <Tarjeta>
                    <h3 className="text-sm font-medium text-slate-600">Pendiente por entregar</h3>
                    <p className="mt-1 text-2xl font-bold">{dinero(datos.pendiente.total)}</p>
                    <p className="text-sm text-slate-600">
                      {datos.pendiente.pedidos} {datos.pendiente.pedidos === 1 ? 'pedido pendiente' : 'pedidos pendientes'}
                      {datos.pendiente.saldos > 0 &&
                        ` (${datos.pendiente.saldos} ${datos.pendiente.saldos === 1 ? 'es saldo' : 'son saldos'} de entregas parciales)`}
                    </p>
                  </Tarjeta>
                </Link>
              </Seccion>
            </div>
          )}
        </EstadoCarga>
      </div>
    </Pantalla>
  )
}

function Seccion({ titulo, detalle, children }: { titulo: string; detalle: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="mb-2 flex items-baseline justify-between gap-3">
        <span className="text-sm font-semibold uppercase tracking-wide text-slate-500">{titulo}</span>
        <span className="text-sm text-slate-500">{detalle}</span>
      </h2>
      <div className="space-y-3 lg:grid lg:grid-cols-2 lg:items-start lg:gap-4 lg:space-y-0">{children}</div>
    </section>
  )
}
