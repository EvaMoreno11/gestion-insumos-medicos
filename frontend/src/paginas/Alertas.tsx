import { Link, useSearchParams } from 'react-router'
import { alertas, DIAS_POR_VENCER } from '../api/recursos'
import type { LoteAlerta, ProductoResumen } from '../api/tipos'
import { BotonEnlace, EstadoCarga, Etiqueta, Tarjeta, Vacio } from '../componentes/basicos'
import { EtiquetaStock } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { cuandoVence, mesAnio, unir } from '../util/formato'

type Pestana = 'stock' | 'por-vencer' | 'vencidos'

/**
 * Lo que necesita atención: productos con stock igual o menor al mínimo (para saber qué comprar),
 * lotes que vencen pronto y lotes vencidos que aún tienen unidades.
 */
export default function Alertas() {
  const [params, setParams] = useSearchParams()
  const pestana = (params.get('ver') as Pestana | null) ?? 'stock'
  const stockBajo = useCarga(() => alertas.stockBajo())
  const porVencer = useCarga(() => alertas.porVencer())
  const vencidos = useCarga(() => alertas.vencidos())

  const pestanas: { valor: Pestana; titulo: string; cantidad?: number }[] = [
    { valor: 'stock', titulo: 'Stock bajo', cantidad: stockBajo.datos?.length },
    { valor: 'por-vencer', titulo: 'Por vencer', cantidad: porVencer.datos?.length },
    { valor: 'vencidos', titulo: 'Vencidos', cantidad: vencidos.datos?.length },
  ]

  return (
    <Pantalla titulo="Alertas" volver="/" ancho="amplio">
      <div className="mb-3 grid grid-cols-3 gap-1 rounded-xl bg-slate-200 p-1 lg:max-w-xl" role="tablist">
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
            {p.cantidad !== undefined && p.cantidad > 0 && ` (${p.cantidad})`}
          </button>
        ))}
      </div>

      {pestana === 'stock' && (
        <EstadoCarga cargando={stockBajo.cargando} error={stockBajo.error} hayDatos={stockBajo.datos !== undefined}>
          <p className="mb-3 text-sm text-slate-600">
            Productos con stock igual o menor a su mínimo, primero los agotados. Los productos sin stock mínimo no
            avisan. El mínimo se cambia en Productos.
          </p>
          {stockBajo.datos?.length === 0 && <Vacio>Ningún producto está por debajo de su mínimo.</Vacio>}
          <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
            {stockBajo.datos?.map((p) => (
              <li key={p.id}>
                <FilaStockBajo producto={p} />
              </li>
            ))}
          </ul>
        </EstadoCarga>
      )}

      {pestana === 'por-vencer' && (
        <EstadoCarga cargando={porVencer.cargando} error={porVencer.error} hayDatos={porVencer.datos !== undefined}>
          <p className="mb-3 text-sm text-slate-600">
            Lotes que vencen en los próximos {DIAS_POR_VENCER} días. Salen primero en los pedidos.
          </p>
          {porVencer.datos?.length === 0 && <Vacio>Ningún lote vence en los próximos {DIAS_POR_VENCER} días.</Vacio>}
          <ListaLotes lotes={porVencer.datos ?? []} />
        </EstadoCarga>
      )}

      {pestana === 'vencidos' && (
        <EstadoCarga cargando={vencidos.cargando} error={vencidos.error} hayDatos={vencidos.datos !== undefined}>
          <p className="mb-3 text-sm text-slate-600">
            Lotes vencidos que aún tienen unidades. La app nunca los usa en un pedido.
          </p>
          {vencidos.datos?.length === 0 && <Vacio>No hay lotes vencidos con unidades.</Vacio>}
          <ListaLotes lotes={vencidos.datos ?? []} />
        </EstadoCarga>
      )}
    </Pantalla>
  )
}

function FilaStockBajo({ producto }: { producto: ProductoResumen }) {
  return (
    <Tarjeta>
      <Link to={`/inventario/${producto.id}`} className="flex items-start justify-between gap-3">
        <span className="min-w-0">
          <span className="block font-semibold leading-snug">{producto.nombre}</span>
          <span className="block text-sm text-slate-500">{unir(' · ', producto.presentacion, producto.marca)}</span>
          <span className="mt-1 block text-sm text-slate-700">
            Hay <strong>{producto.stockDisponible}</strong> · mínimo {producto.stockMinimo}
          </span>
        </span>
        <EtiquetaStock estado={producto.estadoStock} />
      </Link>
      <div className="mt-3">
        <BotonEnlace a={`/entrada?producto=${producto.id}`} variante="secundario" ancho>
          Llegó: registrar entrada
        </BotonEnlace>
      </div>
    </Tarjeta>
  )
}

function ListaLotes({ lotes }: { lotes: LoteAlerta[] }) {
  return (
    <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
      {lotes.map((l) => (
        <li key={l.loteId}>
          <Link
            to={`/inventario/${l.productoId}`}
            className="flex items-start justify-between gap-3 rounded-2xl border border-slate-200 bg-white p-3 active:bg-slate-50"
          >
            <span className="min-w-0">
              <span className="block font-semibold leading-snug">{l.productoNombre}</span>
              <span className="block text-sm text-slate-500">{unir(' · ', l.presentacion, l.marca)}</span>
              <span className="mt-1 block text-sm text-slate-700">
                Lote {l.numeroLote ?? 's/n'} · vence {mesAnio(l.fechaVencimiento)} · {l.cantidad}{' '}
                {l.cantidad === 1 ? 'unidad' : 'unidades'}
              </span>
            </span>
            <Etiqueta color={l.diasParaVencer < 0 ? 'rojo' : 'ambar'}>{cuandoVence(l.diasParaVencer)}</Etiqueta>
          </Link>
        </li>
      ))}
    </ul>
  )
}
