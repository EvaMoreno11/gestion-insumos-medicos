import { Link, useSearchParams } from 'react-router'
import { alertas } from '../api/recursos'
import type { LoteAlerta, ProductoResumen } from '../api/tipos'
import { ESTILO_CAMPO, EstadoCarga, Etiqueta, Tarjeta, Vacio } from '../componentes/basicos'
import { AgregarAPorComprar, EtiquetaStock } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga, useEnPorComprar } from '../hooks'
import { categoriasConStockBajo } from '../util/alertas'
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
  // La categoría va en la dirección: al volver de un producto sigue elegida
  const categorias = categoriasConStockBajo(stockBajo.datos ?? [])
  const categoriaId = Number(params.get('categoria')) || null
  const categoria = categorias.find((c) => c.id === categoriaId)
  const productosStockBajo = (stockBajo.datos ?? []).filter((p) => !categoria || p.categoriaId === categoria.id)
  const enPorComprar = useEnPorComprar()

  function cambiarParametro(clave: string, valor: string | null) {
    setParams(
      (actual) => {
        const nuevos = new URLSearchParams(actual)
        if (valor) {
          nuevos.set(clave, valor)
        } else {
          nuevos.delete(clave)
        }
        return nuevos
      },
      { replace: true },
    )
  }
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
            onClick={() => cambiarParametro('ver', p.valor)}
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
          {stockBajo.datos?.length === 0 ? (
            <Vacio>Ningún producto está por debajo de su mínimo.</Vacio>
          ) : (
            <div className="mb-3 lg:max-w-xl">
              <select
                aria-label="Categoría"
                className={ESTILO_CAMPO}
                value={categoria?.id ?? ''}
                onChange={(e) => cambiarParametro('categoria', e.target.value)}
              >
                <option value="">Todas las categorías ({stockBajo.datos?.length})</option>
                {categorias.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nombre} ({c.cantidad})
                  </option>
                ))}
              </select>
              <p className="mt-2 text-sm text-slate-500">
                {productosStockBajo.length} {productosStockBajo.length === 1 ? 'producto' : 'productos'}
                {categoria ? ` en ${categoria.nombre}` : ''}, primero los agotados
              </p>
            </div>
          )}
          <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:gap-3 lg:space-y-0">
            {productosStockBajo.map((p) => (
              <li key={p.id}>
                <FilaStockBajo
                  producto={p}
                  enPorComprar={enPorComprar.tiene(p.id)}
                  onAgregado={() => enPorComprar.marcar(p.id)}
                />
              </li>
            ))}
          </ul>
        </EstadoCarga>
      )}

      {pestana === 'por-vencer' && (
        <EstadoCarga cargando={porVencer.cargando} error={porVencer.error} hayDatos={porVencer.datos !== undefined}>
          {porVencer.datos?.length === 0 && <Vacio>Ningún lote vence en el próximo año.</Vacio>}
          <ListaLotes lotes={porVencer.datos ?? []} />
        </EstadoCarga>
      )}

      {pestana === 'vencidos' && (
        <EstadoCarga cargando={vencidos.cargando} error={vencidos.error} hayDatos={vencidos.datos !== undefined}>
          {vencidos.datos?.length === 0 && <Vacio>No hay lotes vencidos con unidades.</Vacio>}
          <ListaLotes lotes={vencidos.datos ?? []} />
        </EstadoCarga>
      )}
    </Pantalla>
  )
}

/**
 * Un producto con stock bajo y el botón "Agregar a Por comprar": de un toque, sin cantidad (se pone después en
 * Por comprar si hace falta). Si ya está en Por comprar, agregado a mano o porque falta para un pedido, se ve
 * "✓ En Por comprar".
 */
function FilaStockBajo({
  producto,
  enPorComprar,
  onAgregado,
}: {
  producto: ProductoResumen
  enPorComprar: boolean
  onAgregado: () => void
}) {
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
        <AgregarAPorComprar productoId={producto.id} enPorComprar={enPorComprar} onAgregado={onAgregado} />
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
