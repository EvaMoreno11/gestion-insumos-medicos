import { useEffect, useId, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { movimientos, productos } from '../api/recursos'
import type { ProductoDetalle } from '../api/tipos'
import { Aviso, Boton, Campo, ESTILO_CAMPO, Tarjeta } from '../componentes/basicos'
import { BuscadorProductos, CampoMesAnio } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import {
  dinero,
  dineroExacto,
  leerCosto,
  leerEntero,
  limpiarCosto,
  mesAnio,
  mesAnioAFecha,
  unir,
} from '../util/formato'
import EntradasRegistradas from './EntradasRegistradas'

const PESTANAS = [
  { clave: 'registrar', titulo: 'Registrar' },
  { clave: 'registradas', titulo: 'Entradas registradas' },
] as const

/**
 * Registrar entrada: la pestaña Registrar para ingresar una compra de corrido y la pestaña Entradas registradas
 * para consultar lo que llegó. La pestaña va en la dirección (?pestana=registradas), así al volver de un producto
 * se sigue en la misma lista.
 */
export default function Entrada() {
  const [params, setParams] = useSearchParams()
  const pestana = params.get('pestana') === 'registradas' ? 'registradas' : 'registrar'

  return (
    <Pantalla titulo="Registrar entrada" volver="/" ancho="amplio">
      <div className="mb-3 grid grid-cols-2 gap-1 rounded-xl bg-slate-200 p-1 lg:max-w-xl" role="tablist">
        {PESTANAS.map((p) => (
          <button
            key={p.clave}
            type="button"
            role="tab"
            aria-selected={pestana === p.clave}
            onClick={() => {
              if (pestana !== p.clave) {
                setParams(p.clave === 'registradas' ? { pestana: p.clave } : {}, { replace: true })
              }
            }}
            className={`min-h-10 rounded-lg text-sm font-semibold ${
              pestana === p.clave ? 'bg-white text-marca shadow-sm' : 'text-slate-600'
            }`}
          >
            {p.titulo}
          </button>
        ))}
      </div>

      {/* Al cambiar de pestaña, lo de Registrar se borra (como al salir de la pantalla): las entradas ya están guardadas */}
      {pestana === 'registradas' ? <EntradasRegistradas /> : <Registrar />}
    </Pantalla>
  )
}

interface Registrada {
  producto: string
  lote: string | null
  vencimiento: string | null
  cantidad: number
  costo: number
  stock: number
}

/**
 * Registro de mercadería que llega, pensado para ingresar una compra de corrido:
 * producto → lote, vencimiento, cantidad y costo → "Guardar y siguiente".
 */
function Registrar() {
  const [params] = useSearchParams()
  const [producto, setProducto] = useState<ProductoDetalle>()
  const [lote, setLote] = useState('')
  const [vencimiento, setVencimiento] = useState('')
  const [cantidad, setCantidad] = useState('')
  const [costo, setCosto] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [registradas, setRegistradas] = useState<Registrada[]>([])

  /** Al elegir el producto, el costo viene lleno con el último de compra: solo se cambia si varió. */
  function mostrar(p: ProductoDetalle) {
    setProducto(p)
    setLote('')
    setVencimiento('')
    setCantidad('')
    setCosto(p.costo === null ? '' : String(p.costo))
  }

  async function elegir(id: number) {
    setError(null)
    try {
      mostrar(await productos.obtener(id))
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
      .then((p) => vigente && mostrar(p))
      .catch((e: unknown) => vigente && setError(mensajeDe(e)))
    return () => {
      vigente = false
    }
  }, [preseleccionado])

  const fecha = mesAnioAFecha(vencimiento)
  const cantidadValida = leerEntero(cantidad)
  const costoLeido = leerCosto(costo)
  const costoValido = costoLeido && 'valor' in costoLeido ? costoLeido.valor : null
  const listo =
    producto !== undefined &&
    fecha !== undefined &&
    cantidadValida !== null &&
    cantidadValida > 0 &&
    costoValido !== null &&
    costoValido >= 0

  async function guardar() {
    if (!producto || fecha === undefined || cantidadValida === null || costoValido === null) {
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
        costo: costoValido,
      })
      const movimiento = resultado.movimientos[0]
      setRegistradas((anteriores) => [
        {
          producto: producto.nombre,
          lote: movimiento.numeroLote,
          vencimiento: movimiento.fechaVencimiento,
          cantidad: cantidadValida,
          costo: costoValido,
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
    <div className="space-y-3">
      {error && <Aviso tipo="error">{error}</Aviso>}

      <div className="space-y-3 lg:grid lg:grid-cols-2 lg:items-start lg:gap-6 lg:space-y-0">
        <div className="lg:sticky lg:top-24">
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

              {producto.ultimoLote && !producto.ultimoLote.vencido && (
                <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-50 p-3">
                  <p className="min-w-0 text-sm text-slate-700">
                    Lote de la última compra:{' '}
                    <strong>
                      {unir(
                        ' · ',
                        producto.ultimoLote.numeroLote ?? 'sin número',
                        producto.ultimoLote.fechaVencimiento
                          ? `vence ${mesAnio(producto.ultimoLote.fechaVencimiento)}`
                          : null,
                      )}
                    </strong>
                  </p>
                  <Boton
                    variante="secundario"
                    className="min-h-10 shrink-0 px-3 text-sm"
                    onClick={() => {
                      const ultimo = producto.ultimoLote
                      if (ultimo) {
                        setLote(ultimo.numeroLote ?? '')
                        setVencimiento(mesAnio(ultimo.fechaVencimiento))
                      }
                    }}
                  >
                    Usar este lote
                  </Boton>
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
                <div className="grid grid-cols-2 gap-3">
                  <Campo
                    etiqueta={`Cantidad (${producto.unidadMedida.toLowerCase()})`}
                    inputMode="numeric"
                    autoComplete="off"
                    value={cantidad}
                    onChange={(e) => setCantidad(e.target.value.replace(/\D/g, ''))}
                  />
                  <CampoCosto
                    valor={costo}
                    onCambio={setCosto}
                    unidad={producto.unidadMedida.toLowerCase()}
                    ultimo={producto.costo}
                  />
                </div>
                <Boton type="submit" ancho disabled={!listo || ocupado}>
                  {ocupado ? 'Guardando…' : 'Guardar y siguiente'}
                </Boton>
              </form>
            </Tarjeta>
          )}
        </div>

        {registradas.length > 0 && (
          <section>
            <h2 className="mb-2 mt-4 text-sm font-semibold uppercase tracking-wide text-slate-500 lg:mt-0">
              Ingresado ahora ({registradas.length})
            </h2>
            <ul className="space-y-2">
              {registradas.map((r, i) => (
                <li key={registradas.length - i} className="rounded-2xl border border-emerald-200 bg-emerald-50 p-3">
                  <div className="flex items-start justify-between gap-3">
                    <span className="min-w-0">
                      <span className="block font-medium leading-snug">{r.producto}</span>
                      <span className="block text-sm text-slate-600">
                        {unir(
                          ' · ',
                          r.lote ? `Lote ${r.lote}` : 'Sin lote',
                          r.vencimiento ? `vence ${mesAnio(r.vencimiento)}` : null,
                          `${dinero(r.costo)} c/u`,
                        )}
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
    </div>
  )
}

/**
 * Costo por unidad: se escribe directo (8.50) o como división (30÷12) para lo que se compra por docena o paquete
 * y se registra por unidad; debajo se ve el resultado, que es lo que se guarda. El botón ÷ va al lado del campo
 * porque el teclado numérico del celular no lo tiene, y al tocarlo el teclado no se cierra. En la PC también se
 * puede escribir "/".
 */
function CampoCosto({
  valor,
  onCambio,
  unidad,
  ultimo,
}: {
  valor: string
  onCambio: (valor: string) => void
  unidad: string
  ultimo: number | null
}) {
  const id = useId()
  const campo = useRef<HTMLInputElement>(null)
  const costo = leerCosto(valor)
  const error = costo && 'error' in costo && !costo.pendiente ? costo.error : null
  const falta = costo && 'error' in costo && costo.pendiente ? costo.error : null
  const resultado = costo && 'valor' in costo && costo.division ? costo.valor : null

  function ponerDivision() {
    const input = campo.current
    if (!valor.includes('÷')) {
      const inicio = input?.selectionStart ?? valor.length
      const fin = input?.selectionEnd ?? valor.length
      onCambio(valor.slice(0, inicio) + '÷' + valor.slice(fin))
      // Cuando ya se ve el ÷, el cursor queda justo después para seguir escribiendo
      requestAnimationFrame(() => input?.setSelectionRange(inicio + 1, inicio + 1))
    }
    input?.focus()
  }

  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-slate-700">
        Costo por {unidad} (S/)
      </label>
      <div className="flex gap-2">
        <input
          id={id}
          ref={campo}
          inputMode="decimal"
          autoComplete="off"
          placeholder="0.00"
          value={valor}
          onChange={(e) => onCambio(limpiarCosto(e.target.value))}
          aria-invalid={error ? true : undefined}
          aria-describedby={`${id}-ayuda`}
          className={`${ESTILO_CAMPO} min-w-0 ${error ? 'border-red-400' : ''}`}
        />
        <button
          type="button"
          aria-label="Dividir (costo de una docena o un paquete)"
          title="Dividir"
          // Sin esto, tocar el botón le quita el foco al campo y el celular cierra el teclado
          onMouseDown={(e) => e.preventDefault()}
          onClick={ponerDivision}
          className="min-h-12 w-12 shrink-0 rounded-xl border border-slate-300 bg-white text-2xl font-semibold text-marca active:bg-slate-100"
        >
          ÷
        </button>
      </div>
      <div id={`${id}-ayuda`}>
        {resultado !== null && (
          <span className="mt-1 block text-sm font-semibold text-emerald-700">
            = {dineroExacto(resultado)} por {unidad}
          </span>
        )}
        <span className={`mt-1 block text-sm ${error ? 'text-red-700' : 'text-slate-500'}`}>
          {error ??
            falta ??
            (ultimo === null ? 'Aún no tiene costo: escríbelo de la boleta' : `Último: ${dinero(ultimo)}. Cámbialo si varió`)}
        </span>
      </div>
    </div>
  )
}
