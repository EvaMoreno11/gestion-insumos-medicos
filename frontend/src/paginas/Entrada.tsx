import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { movimientos, productos } from '../api/recursos'
import type { ProductoDetalle } from '../api/tipos'
import { Aviso, Boton, Campo, Tarjeta } from '../componentes/basicos'
import { BuscadorProductos, CampoMesAnio } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { leerEntero, mesAnio, mesAnioAFecha, unir } from '../util/formato'

interface Registrada {
  producto: string
  lote: string | null
  vencimiento: string | null
  cantidad: number
  stock: number
}

/**
 * Registro de mercadería que llega, pensado para ingresar una compra de corrido:
 * producto → lote, vencimiento y cantidad → "Guardar y siguiente".
 */
export default function Entrada() {
  const [params] = useSearchParams()
  const [producto, setProducto] = useState<ProductoDetalle>()
  const [lote, setLote] = useState('')
  const [vencimiento, setVencimiento] = useState('')
  const [cantidad, setCantidad] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [registradas, setRegistradas] = useState<Registrada[]>([])

  async function elegir(id: number) {
    setError(null)
    try {
      setProducto(await productos.obtener(id))
      setLote('')
      setVencimiento('')
      setCantidad('')
    } catch (e) {
      setError(mensajeDe(e))
    }
  }

  // Permite llegar con el producto ya elegido: /entrada?producto=12
  const preseleccionado = params.get('producto')
  useEffect(() => {
    if (!preseleccionado) {
      return
    }
    let vigente = true
    productos
      .obtener(Number(preseleccionado))
      .then((p) => vigente && setProducto(p))
      .catch((e: unknown) => vigente && setError(mensajeDe(e)))
    return () => {
      vigente = false
    }
  }, [preseleccionado])

  const fecha = mesAnioAFecha(vencimiento)
  const cantidadValida = leerEntero(cantidad)
  const listo = producto !== undefined && fecha !== undefined && cantidadValida !== null && cantidadValida > 0

  async function guardar() {
    if (!producto || fecha === undefined || cantidadValida === null) {
      return
    }
    setOcupado(true)
    setError(null)
    try {
      const resultado = await movimientos.entrada({
        productoId: producto.id,
        numeroLote: lote.trim() === '' ? null : lote.trim(),
        fechaVencimiento: fecha,
        cantidad: cantidadValida,
      })
      const movimiento = resultado.movimientos[0]
      setRegistradas((anteriores) => [
        {
          producto: producto.nombre,
          lote: movimiento.numeroLote,
          vencimiento: movimiento.fechaVencimiento,
          cantidad: cantidadValida,
          stock: resultado.stockDisponible,
        },
        ...anteriores,
      ])
      setProducto(undefined) // listo para el siguiente producto
    } catch (e) {
      setError(mensajeDe(e))
    } finally {
      setOcupado(false)
    }
  }

  return (
    <Pantalla titulo="Registrar entrada" volver="/">
      <div className="space-y-3">
        {error && <Aviso tipo="error">{error}</Aviso>}

        {!producto ? (
          <Tarjeta>
            <BuscadorProductos onElegir={(p) => elegir(p.id)} placeholder="¿Qué producto llegó?" autoFocus />
          </Tarjeta>
        ) : (
          <Tarjeta>
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <h2 className="font-semibold leading-snug">{producto.nombre}</h2>
                <p className="text-sm text-slate-500">{unir(' · ', producto.presentacion, producto.marca)}</p>
                <p className="text-sm text-slate-500">Hay {producto.stockDisponible} disponibles</p>
              </div>
              <Boton variante="texto" className="min-h-10 shrink-0 px-2" onClick={() => setProducto(undefined)}>
                Cambiar
              </Boton>
            </div>

            {producto.lotes.length > 0 && (
              <div className="mt-3">
                <p className="mb-1 text-sm text-slate-600">Si es un lote que ya tienes, tócalo:</p>
                <div className="flex flex-wrap gap-2">
                  {producto.lotes.map((l) => (
                    <button
                      key={l.id}
                      type="button"
                      onClick={() => {
                        setLote(l.numeroLote ?? '')
                        setVencimiento(mesAnio(l.fechaVencimiento))
                      }}
                      className="rounded-full border border-slate-300 bg-slate-50 px-3 py-1.5 text-sm active:bg-slate-200"
                    >
                      {l.numeroLote ?? 'Sin número'}
                      {l.fechaVencimiento ? ` · ${mesAnio(l.fechaVencimiento)}` : ''}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <form
              className="mt-4 space-y-3"
              onSubmit={(e) => {
                e.preventDefault()
                void guardar()
              }}
            >
              <div className="grid grid-cols-2 gap-3">
                <Campo
                  etiqueta="N° de lote"
                  autoCapitalize="characters"
                  autoComplete="off"
                  autoCorrect="off"
                  value={lote}
                  onChange={(e) => setLote(e.target.value)}
                  ayuda="Vacío si no tiene"
                />
                <CampoMesAnio
                  valor={vencimiento}
                  onCambio={setVencimiento}
                  error={fecha === undefined ? 'Escribe mes y año: 03/2027' : undefined}
                />
              </div>
              <Campo
                etiqueta={`Cantidad (${producto.unidadMedida.toLowerCase()})`}
                inputMode="numeric"
                autoComplete="off"
                value={cantidad}
                onChange={(e) => setCantidad(e.target.value.replace(/\D/g, ''))}
              />
              <Boton type="submit" ancho disabled={!listo || ocupado}>
                {ocupado ? 'Guardando…' : 'Guardar y siguiente'}
              </Boton>
            </form>
          </Tarjeta>
        )}

        {registradas.length > 0 && (
          <section>
            <h2 className="mb-2 mt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Ingresado ahora ({registradas.length})
            </h2>
            <ul className="space-y-2">
              {registradas.map((r, i) => (
                <li key={registradas.length - i} className="rounded-2xl border border-emerald-200 bg-emerald-50 p-3">
                  <div className="flex items-start justify-between gap-3">
                    <span className="min-w-0">
                      <span className="block font-medium leading-snug">{r.producto}</span>
                      <span className="block text-sm text-slate-600">
                        {unir(' · ', r.lote ? `Lote ${r.lote}` : 'Sin lote', r.vencimiento ? `vence ${mesAnio(r.vencimiento)}` : null)}
                      </span>
                    </span>
                    <span className="shrink-0 text-right">
                      <span className="block text-lg font-bold text-emerald-800">+{r.cantidad}</span>
                      <span className="block text-xs text-slate-600">ahora hay {r.stock}</span>
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </Pantalla>
  )
}
