import { useState } from 'react'
import { pedidos } from '../api/recursos'
import type { Cobro, EstadoCobro, MedioPago, Pedido } from '../api/tipos'
import { cuandoVence, dinero, fechaDia, hoyLocal, leerDecimal, leerEntero, textoPlazo } from '../util/formato'
import { Boton, Campo, Etiqueta, Tarjeta } from './basicos'

const MEDIOS: { valor: MedioPago; titulo: string }[] = [
  { valor: 'EFECTIVO', titulo: 'Efectivo' },
  { valor: 'YAPE', titulo: 'Yape' },
  { valor: 'PLIN', titulo: 'Plin' },
  { valor: 'TRANSFERENCIA', titulo: 'Transferencia' },
]

const PLAZOS = [
  { dias: 0, titulo: 'Contado' },
  { dias: 7, titulo: '7 días' },
  { dias: 15, titulo: '15 días' },
  { dias: 30, titulo: '30 días' },
]

function nombreMedio(medio: MedioPago): string {
  return MEDIOS.find((m) => m.valor === medio)?.titulo ?? medio
}

/** Etiqueta del estado del cobro: el texto dice el estado (no solo el color). */
export function EtiquetaCobro({ estado, dias }: { estado: EstadoCobro; dias: number | null }) {
  if (estado === 'PAGADO') {
    return <Etiqueta color="verde">Pagado</Etiqueta>
  }
  if (estado === 'SIN_PLAZO') {
    return <Etiqueta color="gris">Sin plazo</Etiqueta>
  }
  return <Etiqueta color={estado === 'VENCIDO' ? 'rojo' : 'ambar'}>{cuandoVence(dias ?? 0)}</Etiqueta>
}

function Opciones<T extends string | number>({
  opciones,
  valor,
  onCambio,
  nombre,
}: {
  opciones: { valor: T; titulo: string }[]
  valor: T | null
  onCambio: (valor: T) => void
  nombre: string
}) {
  return (
    <div className="flex flex-wrap gap-2" role="radiogroup" aria-label={nombre}>
      {opciones.map((o) => (
        <button
          key={o.valor}
          type="button"
          role="radio"
          aria-checked={valor === o.valor}
          onClick={() => onCambio(o.valor)}
          className={`min-h-11 rounded-xl border-2 px-4 font-semibold ${
            valor === o.valor ? 'border-marca bg-marca-claro text-marca' : 'border-slate-200 bg-white text-slate-700'
          }`}
        >
          {o.titulo}
        </button>
      ))}
    </div>
  )
}

/**
 * Cobro de un pedido despachado: cuánto se debe, el plazo y los pagos. Solo para uso interno:
 * nada de esto sale en la nota. Al despachar no se pregunta nada; se anota aquí cuando se sabe.
 */
export function TarjetaCobro({
  pedido,
  cobro,
  ocupado,
  aplicar,
}: {
  pedido: Pedido
  cobro: Cobro
  ocupado: boolean
  aplicar: (accion: () => Promise<Pedido>) => Promise<boolean>
}) {
  const [panel, setPanel] = useState<'plazo' | 'pago' | 'todo' | null>(null)
  const debe = cobro.saldo > 0

  async function hacer(accion: () => Promise<Pedido>) {
    if (await aplicar(accion)) {
      setPanel(null)
    }
  }

  return (
    <Tarjeta>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-xs uppercase tracking-wide text-slate-500">Pago</h2>
          <p className={`text-xl font-bold ${cobro.estado === 'VENCIDO' ? 'text-red-700' : ''}`}>
            {debe ? `Debe ${dinero(cobro.saldo)}` : 'Pagado'}
          </p>
          {cobro.pagado > 0 && debe && (
            <p className="text-sm text-slate-600">
              Pagó {dinero(cobro.pagado)} de {dinero(cobro.total)}
            </p>
          )}
          <p className="mt-1 text-sm text-slate-600">
            {cobro.plazoDias === null
              ? 'Sin plazo: aún no se anotó cómo paga'
              : `${textoPlazo(cobro.plazoDias)} · vence el ${fechaDia(cobro.vence)}`}
          </p>
        </div>
        <EtiquetaCobro estado={cobro.estado} dias={cobro.dias} />
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        <Boton variante="secundario" disabled={ocupado} onClick={() => setPanel(panel === 'plazo' ? null : 'plazo')}>
          {cobro.plazoDias === null ? 'Poner plazo' : 'Cambiar plazo'}
        </Boton>
        {debe && (
          <>
            <Boton variante="secundario" disabled={ocupado} onClick={() => setPanel(panel === 'pago' ? null : 'pago')}>
              Registrar pago
            </Boton>
            <Boton disabled={ocupado} onClick={() => setPanel(panel === 'todo' ? null : 'todo')}>
              Pagó todo
            </Boton>
          </>
        )}
      </div>

      {panel === 'plazo' && (
        <PanelPlazo
          actual={cobro.plazoDias}
          ocupado={ocupado}
          onGuardar={(dias) => hacer(() => pedidos.definirPlazo(pedido.id, dias))}
        />
      )}
      {panel === 'pago' && (
        <PanelPago
          saldo={cobro.saldo}
          ocupado={ocupado}
          onGuardar={(pago) => hacer(() => pedidos.registrarPago(pedido.id, pago))}
        />
      )}
      {panel === 'todo' && (
        <PanelPagoTotal
          saldo={cobro.saldo}
          ocupado={ocupado}
          onGuardar={(medio) => hacer(() => pedidos.registrarPago(pedido.id, { monto: cobro.saldo, medio }))}
        />
      )}

      {cobro.pagos.length > 0 && (
        <ul className="mt-3 divide-y divide-slate-100 border-t border-slate-100 text-sm">
          {cobro.pagos.map((p) => (
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
                  if (window.confirm(`¿Borrar el pago de ${dinero(p.monto)}? Solo si se registró por error.`)) {
                    void aplicar(() => pedidos.eliminarPago(pedido.id, p.id))
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
    </Tarjeta>
  )
}

function PanelPlazo({
  actual,
  ocupado,
  onGuardar,
}: {
  actual: number | null
  ocupado: boolean
  onGuardar: (dias: number | null) => void
}) {
  const [dias, setDias] = useState(actual === null ? '' : String(actual))
  const valor = dias.trim() === '' ? null : leerEntero(dias)
  const valido = dias.trim() === '' || (valor !== null && valor >= 0 && valor <= 365)

  return (
    <div className="mt-3 space-y-3 border-t border-slate-100 pt-3">
      <Opciones
        nombre="Plazo de pago"
        opciones={PLAZOS.map((p) => ({ valor: p.dias, titulo: p.titulo }))}
        valor={valor}
        onCambio={(d) => setDias(String(d))}
      />
      <Campo
        etiqueta="Días de crédito"
        inputMode="numeric"
        value={dias}
        onChange={(e) => setDias(e.target.value.replace(/\D/g, ''))}
        ayuda="Desde el día del despacho. 0 = contado. Vacío = sin plazo."
        error={valido ? undefined : 'Debe estar entre 0 y 365'}
      />
      <p className="text-sm text-slate-500">Quedará como el plazo habitual de este cliente para sus próximos pedidos.</p>
      <Boton ancho disabled={ocupado || !valido} onClick={() => onGuardar(valor)}>
        Guardar plazo
      </Boton>
    </div>
  )
}

function PanelPago({
  saldo,
  ocupado,
  onGuardar,
}: {
  saldo: number
  ocupado: boolean
  onGuardar: (pago: { monto: number; medio: MedioPago; fecha: string; nota?: string }) => void
}) {
  const hoy = hoyLocal()
  const [monto, setMonto] = useState(saldo.toFixed(2))
  const [fecha, setFecha] = useState(hoy)
  const [medio, setMedio] = useState<MedioPago | null>(null)
  const [nota, setNota] = useState('')
  const valor = leerDecimal(monto)
  const pasado = valor !== null && valor > saldo
  const listo = valor !== null && valor > 0 && !pasado && medio !== null && fecha !== '' && fecha <= hoy

  return (
    <form
      className="mt-3 space-y-3 border-t border-slate-100 pt-3"
      onSubmit={(e) => {
        e.preventDefault()
        if (listo && valor !== null && medio !== null) {
          onGuardar({ monto: valor, medio, fecha, nota: nota.trim() === '' ? undefined : nota })
        }
      }}
    >
      <div className="grid grid-cols-2 gap-3">
        <Campo
          etiqueta="Monto (S/)"
          inputMode="decimal"
          value={monto}
          onChange={(e) => setMonto(e.target.value.replace(/[^\d.,]/g, ''))}
          onFocus={(e) => e.currentTarget.select()}
          error={pasado ? `Falta cobrar ${dinero(saldo)}` : undefined}
        />
        <Campo etiqueta="Fecha" type="date" max={hoy} value={fecha} onChange={(e) => setFecha(e.target.value)} />
      </div>
      <Opciones nombre="Medio de pago" opciones={MEDIOS} valor={medio} onCambio={setMedio} />
      <Campo etiqueta="Nota" value={nota} onChange={(e) => setNota(e.target.value)} maxLength={200} ayuda="Opcional" />
      <Boton type="submit" ancho disabled={ocupado || !listo}>
        Guardar pago
      </Boton>
    </form>
  )
}

function PanelPagoTotal({
  saldo,
  ocupado,
  onGuardar,
}: {
  saldo: number
  ocupado: boolean
  onGuardar: (medio: MedioPago) => void
}) {
  const [medio, setMedio] = useState<MedioPago | null>(null)
  return (
    <div className="mt-3 space-y-3 border-t border-slate-100 pt-3">
      <p className="text-sm text-slate-600">Hoy pagó los {dinero(saldo)} que faltaban. ¿Cómo pagó?</p>
      <Opciones nombre="Medio de pago" opciones={MEDIOS} valor={medio} onCambio={setMedio} />
      <Boton ancho disabled={ocupado || medio === null} onClick={() => medio !== null && onGuardar(medio)}>
        Registrar {dinero(saldo)} como pagado
      </Boton>
    </div>
  )
}
