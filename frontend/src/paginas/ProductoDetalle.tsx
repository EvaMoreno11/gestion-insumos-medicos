import { useParams } from 'react-router'
import { productos } from '../api/recursos'
import type { Lote } from '../api/tipos'
import { BotonEnlace, EstadoCarga, Etiqueta, Tarjeta } from '../componentes/basicos'
import { EtiquetaStock } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { dinero, mesAnio, unir } from '../util/formato'

function AvisoDeLote({ lote }: { lote: Lote }) {
  if (lote.vencido) {
    return <Etiqueta color="rojo">Vencido</Etiqueta>
  }
  if (lote.diasParaVencer !== null && lote.diasParaVencer <= 60) {
    return <Etiqueta color="ambar">Vence en {lote.diasParaVencer} días</Etiqueta>
  }
  return null
}

function Dato({ etiqueta, valor, destacado }: { etiqueta: string; valor: number; destacado?: boolean }) {
  return (
    <div className="text-center">
      <div className={`font-bold leading-none ${destacado ? 'text-3xl' : 'text-xl text-slate-700'}`}>{valor}</div>
      <div className="mt-1 text-xs text-slate-500">{etiqueta}</div>
    </div>
  )
}

export default function ProductoDetalle() {
  const id = Number(useParams().id)
  const { datos: p, cargando, error } = useCarga(() => productos.obtener(id), id)

  return (
    <Pantalla titulo="Producto" volver="/inventario">
      <EstadoCarga cargando={cargando} error={error} hayDatos={p !== undefined}>
        {p && (
          <div className="space-y-3">
            <Tarjeta>
              <div className="flex items-start justify-between gap-3">
                <h2 className="text-lg font-semibold leading-snug">{p.nombre}</h2>
                <EtiquetaStock estado={p.estadoStock} />
              </div>
              <p className="mt-1 text-sm text-slate-500">{unir(' · ', p.presentacion, p.marca, p.categoria)}</p>
              <p className="text-sm text-slate-500">Código {p.codigo}</p>
              {!p.activo && (
                <div className="mt-2">
                  <Etiqueta color="gris">Desactivado</Etiqueta>
                </div>
              )}

              <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-4">
                <Dato etiqueta={`Disponible (${p.unidadMedida.toLowerCase()})`} valor={p.stockDisponible} destacado />
                <Dato etiqueta="Separado para pedidos" valor={p.stockSeparado} />
                <Dato etiqueta="Libre" valor={p.stockLibre} />
              </div>
              {p.stockVencido > 0 && (
                <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">
                  Además hay {p.stockVencido} vencidas que no se pueden despachar.
                </p>
              )}
            </Tarjeta>

            <Tarjeta>
              <h3 className="font-semibold">Lotes</h3>
              {p.lotes.length === 0 ? (
                <p className="mt-2 text-sm text-slate-500">No hay lotes con unidades.</p>
              ) : (
                <ul className="mt-2 divide-y divide-slate-100">
                  {p.lotes.map((l) => (
                    <li key={l.id} className="flex items-center gap-3 py-2">
                      <span className="min-w-0 flex-1">
                        <span className="block font-medium">{l.numeroLote ?? 'Sin número de lote'}</span>
                        <span className="block text-sm text-slate-500">
                          {l.fechaVencimiento ? `Vence ${mesAnio(l.fechaVencimiento)}` : 'No vence'}
                        </span>
                      </span>
                      <AvisoDeLote lote={l} />
                      <span className="w-12 text-right text-lg font-bold">{l.cantidad}</span>
                    </li>
                  ))}
                </ul>
              )}
            </Tarjeta>

            <Tarjeta>
              <h3 className="font-semibold">Precios</h3>
              <p className="text-xs text-slate-500">Uso interno: costo más el porcentaje de cada categoría.</p>
              {p.costo === null ? (
                <p className="mt-2 text-sm text-amber-800">Este producto aún no tiene costo registrado.</p>
              ) : (
                <div className="mt-3 grid grid-cols-4 gap-2">
                  <div className="rounded-xl bg-slate-100 px-1 py-2 text-center">
                    <div className="text-xs text-slate-500">Costo</div>
                    <div className="text-sm font-semibold">{dinero(p.costo)}</div>
                  </div>
                  {p.precios.map((precio) => (
                    <div key={precio.letra} className="rounded-xl bg-marca-claro px-1 py-2 text-center">
                      <div className="text-xs text-marca">
                        <strong>{precio.letra}</strong> · {Number(precio.porcentaje)} %
                      </div>
                      <div className="text-sm font-semibold">{dinero(precio.precio)}</div>
                    </div>
                  ))}
                </div>
              )}
            </Tarjeta>

            <div className="grid grid-cols-2 gap-3">
              <BotonEnlace a={`/entrada?producto=${p.id}`} ancho>
                Registrar entrada
              </BotonEnlace>
              <BotonEnlace a={`/productos/${p.id}`} variante="secundario" ancho>
                Editar
              </BotonEnlace>
            </div>
          </div>
        )}
      </EstadoCarga>
    </Pantalla>
  )
}
