import { useId, useState } from 'react'
import { useSearchParams } from 'react-router'
import { mensajeDe } from '../api/http'
import { proveedores } from '../api/recursos'
import type { DeudaProveedor, DeudaProveedorDatos, Proveedor } from '../api/tipos'
import { Aviso, Boton, Campo, EstadoCarga, Etiqueta, Tarjeta, Vacio } from '../componentes/basicos'
import { PanelPago, PanelPagoTotal } from '../componentes/cobro'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { dinero, fechaDia, haceDias, hoyLocal, leerDecimal, textoComparable, unir } from '../util/formato'
import { nombreMedio } from '../util/pagos'

/** Deudas cargadas con "Ver más" para una lista concreta. */
interface Extra {
  clave: string
  deudas: DeudaProveedor[]
  pagina: number
}

/**
 * Pagos a proveedores: lo que se les debe por las compras que no se pagan al contado y lo que se les va pagando.
 * Solo es un control para que no se olvide nada: no cambia el inventario ni el Resumen.
 */
export default function Proveedores() {
  const [params, setParams] = useSearchParams()
  const pagadas = params.get('ver') === 'pagadas'
  const [version, setVersion] = useState(0)
  const clave = `${pagadas}|${version}`
  const resumen = useCarga(() => proveedores.resumen(), version)
  const primera = useCarga(() => proveedores.deudas(pagadas), clave)
  const nombres = useCarga(() => proveedores.listar(), version)
  const [anotando, setAnotando] = useState(false)

  const [extra, setExtra] = useState<Extra>()
  const [cargandoMas, setCargandoMas] = useState(false)
  const [errorMas, setErrorMas] = useState<string | null>(null)
  const mas = extra?.clave === clave && !primera.cargando ? extra : undefined
  const lista = [...(primera.datos?.contenido ?? []), ...(mas?.deudas ?? [])]
  const total = primera.datos?.totalElementos ?? 0
  const recargar = () => setVersion((v) => v + 1)

  async function verMas() {
    setCargandoMas(true)
    setErrorMas(null)
    try {
      const pagina = (mas?.pagina ?? 0) + 1
      const r = await proveedores.deudas(pagadas, pagina)
      setExtra({ clave, deudas: [...(mas?.deudas ?? []), ...r.contenido], pagina })
    } catch (e) {
      setErrorMas(mensajeDe(e))
    } finally {
      setCargandoMas(false)
    }
  }

  const porPagar = resumen.datos?.deudasPorPagar ?? 0

  return (
    <Pantalla
      titulo="Proveedores"
      volver="/"
      ancho="amplio"
      pie={
        anotando ? undefined : (
          <Boton
            ancho
            onClick={() => {
              setAnotando(true)
              window.scrollTo({ top: 0, behavior: 'smooth' })
            }}
          >
            + Anotar deuda
          </Boton>
        )
      }
    >
      <div className="space-y-3">
        <Tarjeta>
          <p className="text-sm font-medium text-slate-600">Falta pagar</p>
          <p className="text-3xl font-bold leading-tight">{dinero(resumen.datos?.porPagar ?? 0)}</p>
          <p className="mt-1 font-medium text-slate-600">
            {porPagar === 0 ? 'No hay deudas por pagar' : `${porPagar} ${porPagar === 1 ? 'deuda' : 'deudas'} por pagar`}
          </p>
        </Tarjeta>

        {anotando && (
          <Tarjeta>
            <h2 className="font-semibold">Anotar deuda</h2>
            <p className="text-sm text-slate-500">Solo las compras que no se pagan al contado.</p>
            <FormularioDeuda
              nombres={nombres.datos ?? []}
              onGuardar={async (datos) => {
                await proveedores.crear(datos)
                setAnotando(false)
                setParams({}, { replace: true })
                recargar()
              }}
              onCancelar={() => setAnotando(false)}
            />
          </Tarjeta>
        )}

        <div className="grid grid-cols-2 gap-1 rounded-xl bg-slate-200 p-1 lg:max-w-md" role="tablist">
          {[
            { valor: false, titulo: `Por pagar${porPagar > 0 ? ` (${porPagar})` : ''}` },
            { valor: true, titulo: 'Pagadas' },
          ].map((p) => (
            <button
              key={p.titulo}
              type="button"
              role="tab"
              aria-selected={pagadas === p.valor}
              onClick={() => setParams(p.valor ? { ver: 'pagadas' } : {}, { replace: true })}
              className={`min-h-10 rounded-lg px-1 text-sm font-semibold ${
                pagadas === p.valor ? 'bg-white text-marca shadow-sm' : 'text-slate-600'
              }`}
            >
              {p.titulo}
            </button>
          ))}
        </div>

        <EstadoCarga cargando={primera.cargando} error={primera.error} hayDatos={primera.datos !== undefined}>
          <p className="mb-3 text-sm text-slate-600">
            {pagadas
              ? 'Primero las que se terminaron de pagar hace menos.'
              : 'Primero las más antiguas, para que ninguna se olvide.'}
          </p>
          {lista.length === 0 && (
            <Vacio>{pagadas ? 'Todavía no hay deudas pagadas.' : 'No hay deudas por pagar.'}</Vacio>
          )}
          <ul className="space-y-2 lg:grid lg:grid-cols-2 lg:items-start lg:gap-3 lg:space-y-0">
            {lista.map((d) => (
              <li key={d.id}>
                <TarjetaDeuda deuda={d} nombres={nombres.datos ?? []} onCambio={recargar} />
              </li>
            ))}
          </ul>
          {errorMas && (
            <div className="mt-3">
              <Aviso tipo="error">{errorMas}</Aviso>
            </div>
          )}
          {lista.length < total && (
            <Boton variante="secundario" ancho className="mt-4" disabled={cargandoMas} onClick={() => void verMas()}>
              {cargandoMas ? 'Cargando…' : `Ver más (${total - lista.length} más)`}
            </Boton>
          )}
        </EstadoCarga>
      </div>
    </Pantalla>
  )
}

function TarjetaDeuda({
  deuda: d,
  nombres,
  onCambio,
}: {
  deuda: DeudaProveedor
  nombres: Proveedor[]
  onCambio: () => void
}) {
  const [panel, setPanel] = useState<'pago' | 'todo' | 'editar' | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const debe = d.saldo > 0

  async function hacer(accion: () => Promise<unknown>) {
    setOcupado(true)
    setError(null)
    try {
      await accion()
      setPanel(null)
      onCambio()
    } catch (e) {
      setError(mensajeDe(e))
    } finally {
      setOcupado(false)
    }
  }

  return (
    <Tarjeta>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-semibold leading-snug break-words">{d.proveedor}</p>
          <p className="text-sm break-words text-slate-500">
            {unir(' · ', fechaDia(d.fecha), d.nota, debe ? haceDias(d.fecha) : null)}
          </p>
        </div>
        <div className="shrink-0 text-right">
          {debe ? (
            <>
              <span className="block text-lg font-bold">{dinero(d.saldo)}</span>
              <span className="block text-xs text-slate-500">falta pagar</span>
            </>
          ) : (
            <Etiqueta color="verde">Pagada</Etiqueta>
          )}
        </div>
      </div>
      <p className="mt-1 text-sm text-slate-600">
        {debe
          ? `Total ${dinero(d.monto)} · pagado ${dinero(d.pagado)}`
          : `Total ${dinero(d.monto)} · se terminó de pagar el ${fechaDia(d.pagadaEl)}`}
      </p>

      {error && (
        <div className="mt-2">
          <Aviso tipo="error">{error}</Aviso>
        </div>
      )}

      {debe && panel !== 'editar' && (
        <div className="mt-3 flex flex-wrap gap-2">
          <Boton variante="secundario" disabled={ocupado} onClick={() => setPanel(panel === 'pago' ? null : 'pago')}>
            Registrar pago
          </Boton>
          <Boton disabled={ocupado} onClick={() => setPanel(panel === 'todo' ? null : 'todo')}>
            Pagó todo
          </Boton>
        </div>
      )}
      {panel === 'pago' && (
        <PanelPago
          saldo={d.saldo}
          ocupado={ocupado}
          falta="Falta pagar"
          onGuardar={(pago) => void hacer(() => proveedores.registrarPago(d.id, pago))}
        />
      )}
      {panel === 'todo' && (
        <PanelPagoTotal
          saldo={d.saldo}
          ocupado={ocupado}
          texto={`Hoy se le pagaron los ${dinero(d.saldo)} que faltaban. ¿Cómo se pagó?`}
          onGuardar={(medio) => void hacer(() => proveedores.registrarPago(d.id, { monto: d.saldo, medio }))}
        />
      )}
      {panel === 'editar' && (
        <div className="mt-3 border-t border-slate-100 pt-3">
          <FormularioDeuda
            deuda={d}
            nombres={nombres}
            onGuardar={async (datos) => {
              await proveedores.corregir(d.id, datos)
              setPanel(null)
              onCambio()
            }}
            onCancelar={() => setPanel(null)}
          />
        </div>
      )}

      {d.pagos.length > 0 && (
        <ul className="mt-3 divide-y divide-slate-100 border-t border-slate-100 text-sm">
          {d.pagos.map((p) => (
            <li key={p.id} className="flex items-center justify-between gap-3 py-2">
              <span className="min-w-0">
                <span className="block font-medium">
                  {dinero(p.monto)} · {nombreMedio(p.medio)}
                </span>
                <span className="block text-slate-500">
                  {fechaDia(p.fecha)}
                  {p.nota ? ` · ${p.nota}` : ''}
                </span>
              </span>
              <button
                type="button"
                disabled={ocupado}
                aria-label={`Borrar el pago de ${dinero(p.monto)} del ${fechaDia(p.fecha)}`}
                onClick={() => {
                  if (window.confirm(`¿Borrar el pago de ${dinero(p.monto)}? Solo si se anotó por error.`)) {
                    void hacer(() => proveedores.eliminarPago(d.id, p.id))
                  }
                }}
                className="min-h-10 shrink-0 px-2 font-semibold text-red-700"
              >
                Borrar
              </button>
            </li>
          ))}
        </ul>
      )}

      {panel !== 'editar' && (
        <div className="mt-2 flex flex-wrap gap-x-2 border-t border-slate-100 pt-1">
          <Boton variante="texto" className="min-h-10 px-2 text-sm" disabled={ocupado} onClick={() => setPanel('editar')}>
            Editar
          </Boton>
          {d.pagos.length === 0 && (
            <Boton
              variante="texto"
              className="min-h-10 px-2 text-sm text-red-700"
              disabled={ocupado}
              onClick={() => {
                if (window.confirm(`¿Borrar la deuda de ${dinero(d.monto)} con ${d.proveedor}? Solo si se anotó por error.`)) {
                  void hacer(() => proveedores.eliminar(d.id))
                }
              }}
            >
              Borrar deuda
            </Boton>
          )}
        </div>
      )}
    </Tarjeta>
  )
}

/** Anotar o corregir una deuda. El proveedor se elige de la lista o se escribe uno nuevo. */
function FormularioDeuda({
  deuda,
  nombres,
  onGuardar,
  onCancelar,
}: {
  deuda?: DeudaProveedor
  nombres: Proveedor[]
  onGuardar: (datos: DeudaProveedorDatos) => Promise<void>
  onCancelar: () => void
}) {
  const hoy = hoyLocal()
  const idLista = useId()
  const [proveedor, setProveedor] = useState(deuda?.proveedor ?? '')
  const [monto, setMonto] = useState(deuda ? deuda.monto.toFixed(2) : '')
  const [fecha, setFecha] = useState(deuda?.fecha ?? hoy)
  const [nota, setNota] = useState(deuda?.nota ?? '')
  const [ocupado, setOcupado] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const nombre = proveedor.trim().replace(/\s+/g, ' ')
  const existente = nombres.find((p) => textoComparable(p.nombre) === textoComparable(nombre))
  const valor = leerDecimal(monto)
  const montoMal = monto.trim() !== '' && (valor === null || valor <= 0)
  const listo = nombre !== '' && valor !== null && valor > 0 && fecha !== '' && fecha <= hoy

  async function guardar() {
    if (!listo || valor === null) {
      return
    }
    setOcupado(true)
    setError(null)
    try {
      await onGuardar({ proveedor: existente?.nombre ?? nombre, fecha, monto: valor, nota: nota.trim() || undefined })
    } catch (e) {
      setError(mensajeDe(e))
      setOcupado(false)
    }
  }

  return (
    <form
      className="mt-3 space-y-3"
      onSubmit={(e) => {
        e.preventDefault()
        void guardar()
      }}
    >
      {error && <Aviso tipo="error">{error}</Aviso>}
      <Campo
        etiqueta="Proveedor"
        list={idLista}
        autoComplete="off"
        maxLength={120}
        value={proveedor}
        onChange={(e) => setProveedor(e.target.value)}
        ayuda={
          nombre !== '' && !existente ? `Proveedor nuevo: se agregará «${nombre}»` : 'Elige uno de la lista o escribe uno nuevo'
        }
      />
      <datalist id={idLista}>
        {nombres.map((p) => (
          <option key={p.id} value={p.nombre} />
        ))}
      </datalist>
      <div className="grid grid-cols-2 gap-3">
        <Campo
          etiqueta="Monto (S/)"
          inputMode="decimal"
          autoComplete="off"
          placeholder="0.00"
          value={monto}
          onChange={(e) => setMonto(e.target.value.replace(/[^\d.,]/g, ''))}
          error={montoMal ? 'Escribe un monto, p. ej. 1500.00' : undefined}
        />
        <Campo etiqueta="Fecha de la compra" type="date" max={hoy} value={fecha} onChange={(e) => setFecha(e.target.value)} />
      </div>
      <Campo
        etiqueta="Nota (opcional)"
        maxLength={200}
        placeholder="Ej.: Factura F001-234"
        value={nota}
        onChange={(e) => setNota(e.target.value)}
      />
      <div className="grid grid-cols-2 gap-3">
        <Boton variante="secundario" disabled={ocupado} onClick={onCancelar}>
          Cancelar
        </Boton>
        <Boton type="submit" disabled={!listo || ocupado}>
          {ocupado ? 'Guardando…' : 'Guardar'}
        </Boton>
      </div>
    </form>
  )
}
