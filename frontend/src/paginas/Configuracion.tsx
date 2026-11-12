import { useEffect, useState } from 'react'
import { mensajeDe } from '../api/http'
import { negocio, pedidos, sesion as apiSesion } from '../api/recursos'
import { Aviso, Boton, Campo, Cargando, Tarjeta } from '../componentes/basicos'
import { Pantalla } from '../componentes/Pantalla'
import { useSesion } from '../sesion-contexto'
import { leerEntero } from '../util/formato'

/** Datos que aparecen en la nota de pedido: el encabezado del negocio y la numeración. */
export default function Configuracion() {
  const sesion = useSesion()
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
                  const guardado = await negocio.guardar({ nombre, direccion, telefono })
                  sesion.setNegocio(guardado.nombre)
                }, 'Datos del negocio guardados.')
              }}
            >
              <Campo
                etiqueta="Nombre"
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                ayuda="Aparece en el encabezado de la app y de la nota de pedido"
                required
              />
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

          <Cuenta
            usuario={sesion.usuario}
            ocupado={ocupado}
            onCambiarClave={(actual, nueva) =>
              ejecutar(async () => {
                await apiSesion.cambiarClave(actual, nueva)
              }, 'Contraseña cambiada. En los demás dispositivos tendrán que volver a entrar.')
            }
            onSalir={() => void sesion.salir()}
          />
        </div>
      )}
    </Pantalla>
  )
}

/** Usuario actual, cambio de contraseña y cierre de sesión. */
function Cuenta({
  usuario,
  ocupado,
  onCambiarClave,
  onSalir,
}: {
  usuario: string
  ocupado: boolean
  onCambiarClave: (actual: string, nueva: string) => Promise<void>
  onSalir: () => void
}) {
  const [abierto, setAbierto] = useState(false)
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [repetir, setRepetir] = useState('')

  const listo = actual !== '' && nueva.length >= 8 && nueva === repetir

  return (
    <Tarjeta>
      <h2 className="font-semibold">Cuenta</h2>
      <p className="mb-3 text-sm text-slate-500">
        Sesión iniciada como <strong>{usuario}</strong>.
      </p>

      {abierto ? (
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault()
            if (listo) {
              void onCambiarClave(actual, nueva).then(() => {
                setAbierto(false)
                setActual('')
                setNueva('')
                setRepetir('')
              })
            }
          }}
        >
          <Campo
            etiqueta="Contraseña actual"
            type="password"
            autoComplete="current-password"
            value={actual}
            onChange={(e) => setActual(e.target.value)}
          />
          <Campo
            etiqueta="Nueva contraseña"
            type="password"
            autoComplete="new-password"
            value={nueva}
            onChange={(e) => setNueva(e.target.value)}
            ayuda="Al menos 8 caracteres"
          />
          <Campo
            etiqueta="Repite la nueva contraseña"
            type="password"
            autoComplete="new-password"
            value={repetir}
            onChange={(e) => setRepetir(e.target.value)}
            error={repetir !== '' && repetir !== nueva ? 'No coincide' : undefined}
          />
          <p className="text-sm text-slate-500">
            Si pierdes un celular, cambia la contraseña: ese dispositivo dejará de tener acceso.
          </p>
          <div className="flex gap-2">
            <Boton type="submit" className="flex-1" disabled={ocupado || !listo}>
              Guardar contraseña
            </Boton>
            <Boton variante="secundario" onClick={() => setAbierto(false)}>
              Cancelar
            </Boton>
          </div>
        </form>
      ) : (
        <div className="space-y-2">
          <Boton variante="secundario" ancho onClick={() => setAbierto(true)}>
            Cambiar contraseña
          </Boton>
          <Boton
            variante="peligro"
            ancho
            onClick={() => {
              if (window.confirm('¿Cerrar sesión en este dispositivo?')) {
                onSalir()
              }
            }}
          >
            Cerrar sesión
          </Boton>
        </div>
      )}
    </Tarjeta>
  )
}
