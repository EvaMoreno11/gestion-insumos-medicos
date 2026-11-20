import { useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { ErrorApi, mensajeDe } from '../api/http'
import { lotes, productos } from '../api/recursos'
import type { Lote, ProductoDetalle } from '../api/tipos'
import { Aviso, Boton, Campo, EstadoCarga, Tarjeta } from '../componentes/basicos'
import { CampoMesAnio } from '../componentes/dominio'
import { Pantalla } from '../componentes/Pantalla'
import { useCarga } from '../hooks'
import { leerEntero, mesAnio, mesAnioAFecha, numeroDeLote, unir } from '../util/formato'

const NUMERO_VALIDO = /^[\sA-Za-z0-9./-]*$/

/** Corrección de un lote mal registrado (en la app o en el Excel): número, vencimiento y cantidad. */
export default function EditarLote() {
  const params = useParams()
  const productoId = Number(params.id)
  const loteId = Number(params.loteId)
  const { datos: producto, cargando, error } = useCarga(() => productos.obtener(productoId), productoId)
  const lote = producto?.lotes.find((l) => l.id === loteId)

  return (
    <Pantalla titulo="Editar lote" volver={`/inventario/${productoId}`}>
      <EstadoCarga cargando={cargando} error={error} hayDatos={producto !== undefined}>
        {producto &&
          (lote ? (
            <Formulario producto={producto} lote={lote} />
          ) : (
            <Aviso tipo="alerta">Este lote ya no tiene unidades.</Aviso>
          ))}
      </EstadoCarga>
    </Pantalla>
  )
}

function Formulario({ producto, lote }: { producto: ProductoDetalle; lote: Lote }) {
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const [numero, setNumero] = useState(lote.numeroLote ?? '')
  const [vencimiento, setVencimiento] = useState(mesAnio(lote.fechaVencimiento))
  const [cantidad, setCantidad] = useState(String(lote.cantidad))
  const [nota, setNota] = useState('')
  const [error, setError] = useState<string | null>(null)
  /** El servidor avisó que ese número ya es de otro lote que esta pantalla no conocía */
  const [confirmarUnion, setConfirmarUnion] = useState(false)
  const [ocupado, setOcupado] = useState(false)

  const numeroNuevo = numeroDeLote(numero)
  const fecha = mesAnioAFecha(vencimiento)
  const cantidadNueva = leerEntero(cantidad)
  const unidad = producto.unidadMedida.toLowerCase()

  const numeroMalEscrito = !NUMERO_VALIDO.test(numero) || numero.trim().length > 50
  const cambiaCantidad = cantidadNueva !== null && cantidadNueva !== lote.cantidad
  const mesNuevo = fecha === null ? null : fecha?.slice(0, 7)
  const sinCambios =
    numeroNuevo === lote.numeroLote && mesNuevo === (lote.fechaVencimiento?.slice(0, 7) ?? null) && !cambiaCantidad
  const otro =
    numeroNuevo === lote.numeroLote
      ? undefined
      : producto.lotes.find((l) => l.id !== lote.id && l.numeroLote === numeroNuevo)
  const uniendo = otro !== undefined || confirmarUnion
  const listo =
    !numeroMalEscrito && fecha !== undefined && cantidadNueva !== null && cantidadNueva <= 1_000_000 && !sinCambios

  function volver() {
    // Igual que la flecha de la pantalla: si se abrió directo, no hay a dónde retroceder
    if (ubicacion.key !== 'default') {
      navegar(-1)
    } else {
      navegar(`/inventario/${producto.id}`, { replace: true })
    }
  }

  async function guardar() {
    if (!listo || fecha === undefined || cantidadNueva === null) {
      return
    }
    setOcupado(true)
    setError(null)
    try {
      await lotes.corregir(lote.id, {
        numeroLote: numeroNuevo,
        fechaVencimiento: fecha,
        cantidad: cantidadNueva,
        nota: cambiaCantidad && nota.trim() !== '' ? nota.trim() : undefined,
        unir: uniendo,
      })
      volver()
    } catch (e) {
      if (e instanceof ErrorApi && e.status === 409 && e.message.includes('Confirma para unir')) {
        setConfirmarUnion(true)
      }
      setError(mensajeDe(e))
      setOcupado(false)
    }
  }

  const diferencia = cantidadNueva === null ? 0 : cantidadNueva - lote.cantidad

  return (
    <Tarjeta>
      <h2 className="font-semibold leading-snug">{producto.nombre}</h2>
      <p className="text-sm text-slate-500">{unir(' · ', producto.presentacion, producto.marca)}</p>

      <form
        className="mt-4 space-y-3"
        onSubmit={(e) => {
          e.preventDefault()
          void guardar()
        }}
      >
        {error && <Aviso tipo="error">{error}</Aviso>}
        <div className="grid grid-cols-2 gap-3">
          <Campo
            etiqueta="N° de lote"
            autoCapitalize="characters"
            autoComplete="off"
            autoCorrect="off"
            maxLength={50}
            value={numero}
            onChange={(e) => {
              setNumero(e.target.value)
              setConfirmarUnion(false)
            }}
            error={numeroMalEscrito ? 'Solo letras, números, puntos, barras y guiones' : undefined}
            ayuda="Vacío si no tiene"
          />
          <CampoMesAnio
            valor={vencimiento}
            onCambio={setVencimiento}
            error={fecha === undefined ? 'Escribe mes y año: 03/2027' : undefined}
          />
        </div>
        <Campo
          etiqueta={`Cantidad (${unidad})`}
          inputMode="numeric"
          autoComplete="off"
          value={cantidad}
          onChange={(e) => setCantidad(e.target.value.replace(/\D/g, ''))}
          error={cantidad.trim() === '' ? 'Escribe cuántas hay (0 si no queda ninguna)' : undefined}
          ayuda={
            cambiaCantidad
              ? `Antes ${lote.cantidad}: queda como ajuste de ${diferencia > 0 ? '+' : '−'}${Math.abs(diferencia)}`
              : 'Las que hay realmente. Con 0, el lote deja de aparecer'
          }
        />
        {cambiaCantidad && (
          <Campo
            etiqueta="Motivo (opcional)"
            autoComplete="off"
            maxLength={300}
            placeholder="Ej.: error en el Excel"
            value={nota}
            onChange={(e) => setNota(e.target.value)}
          />
        )}

        {otro && (
          <Aviso tipo="alerta">
            <p>
              Ya hay otro lote <strong>{otro.numeroLote ?? 'sin número'}</strong> de este producto (
              {unir(' · ', otro.fechaVencimiento ? `vence ${mesAnio(otro.fechaVencimiento)}` : 'no vence', `hay ${otro.cantidad}`)}
              ).
            </p>
            <p className="mt-1">
              Al guardar se unen en uno solo: quedarán{' '}
              <strong>
                {otro.cantidad} + {cantidadNueva ?? 0} = {otro.cantidad + (cantidadNueva ?? 0)}
              </strong>
              {fecha ? `, que vencen ${mesAnio(fecha)}` : ', sin vencimiento'}.
            </p>
          </Aviso>
        )}

        <p className="text-sm text-slate-500">
          Si corriges el número o el vencimiento, las notas ya despachadas con este lote también se corrigen.
        </p>
        <div className="grid grid-cols-2 gap-3">
          <Boton variante="secundario" onClick={volver}>
            Cancelar
          </Boton>
          <Boton type="submit" disabled={!listo || ocupado}>
            {ocupado ? 'Guardando…' : uniendo ? 'Unir y guardar' : 'Guardar'}
          </Boton>
        </div>
      </form>
    </Tarjeta>
  )
}
