import { useEffect, useState } from 'react'
import { mensajeDe } from '../api/http'
import { negocio, pedidos } from '../api/recursos'
import { Aviso, Boton, Campo, Cargando, Tarjeta } from '../componentes/basicos'
import { Pantalla } from '../componentes/Pantalla'
import { leerEntero } from '../util/formato'

/** Datos que aparecen en la nota de pedido: el encabezado del negocio y la numeración. */
export default function Configuracion() {
  const [cargado, setCargado] = useState(false)
  const [nombre, setNombre] = useState('')
  const [direccion, setDireccion] = useState('')
  const [telefono, setTelefono] = useState('')
  const [siguiente, setSiguiente] = useState('')
  const [proximo, setProximo] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [exito, setExito] = useState<string | null>(null)
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    Promise.all([negocio.obtener(), pedidos.numeracion()])
      .then(([n, numeracion]) => {
        setNombre(n.nombre)
        setDireccion(n.direccion ?? '')
        setTelefono(n.telefono ?? '')
        setSiguiente(String(numeracion.siguiente))
        setProximo(numeracion.proximoNumero)
        setCargado(true)
      })
      .catch((e: unknown) => setError(mensajeDe(e)))
  }, [])

  async function ejecutar(accion: () => Promise<void>, mensaje: string) {
    setOcupado(true)
    setError(null)
    setExito(null)
    try {
      await accion()
      setExito(mensaje)
    } catch (e) {
      setError(mensajeDe(e))
    } finally {
      setOcupado(false)
    }
  }

  const numero = leerEntero(siguiente)

  return (
    <Pantalla titulo="Configuración" volver="/">
      {!cargado ? (
        error ? (
          <Aviso tipo="error">{error}</Aviso>
        ) : (
          <Cargando />
        )
      ) : (
        <div className="space-y-3">
          {error && <Aviso tipo="error">{error}</Aviso>}
          {exito && <Aviso tipo="exito">{exito}</Aviso>}

          <Tarjeta>
            <h2 className="font-semibold">Datos del negocio</h2>
            <p className="mb-3 text-sm text-slate-500">Aparecen en el encabezado de la nota de pedido.</p>
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault()
                void ejecutar(async () => {
                  await negocio.guardar({ nombre, direccion, telefono })
                }, 'Datos del negocio guardados.')
              }}
            >
              <Campo etiqueta="Nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} required />
              <Campo
                etiqueta="Dirección"
                value={direccion}
                onChange={(e) => setDireccion(e.target.value)}
                ayuda="También se usa como punto de partida"
              />
              <Campo etiqueta="Teléfono" inputMode="tel" value={telefono} onChange={(e) => setTelefono(e.target.value)} />
              <Boton type="submit" ancho disabled={ocupado || nombre.trim() === ''}>
                Guardar datos
              </Boton>
            </form>
          </Tarjeta>

          <Tarjeta>
            <h2 className="font-semibold">Numeración de la nota de pedido</h2>
            <p className="mb-3 text-sm text-slate-500">
              La próxima nota saldrá con el número <strong className="whitespace-nowrap">{proximo}</strong>. Cada despacho suma 1.
            </p>
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault()
                if (numero === null || numero < 1) {
                  return
                }
                void ejecutar(async () => {
                  const n = await pedidos.fijarNumeracion(numero)
                  setProximo(n.proximoNumero)
                }, 'Numeración actualizada.')
              }}
            >
              <Campo
                etiqueta="Siguiente número"
                inputMode="numeric"
                value={siguiente}
                onChange={(e) => setSiguiente(e.target.value.replace(/\D/g, '').slice(0, 6))}
              />
              <Boton type="submit" variante="secundario" ancho disabled={ocupado || numero === null || numero < 1}>
                Cambiar numeración
              </Boton>
            </form>
          </Tarjeta>
        </div>
      )}
    </Pantalla>
  )
}
