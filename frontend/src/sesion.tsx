import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { EVENTO_SESION_VENCIDA, mensajeDe } from './api/http'
import { sesion as apiSesion } from './api/recursos'
import type { EstadoSesion } from './api/tipos'
import { Boton, Cargando } from './componentes/basicos'
import { CrearCuenta, Entrar } from './paginas/Entrar'
import { ContextoSesion, nombreVisible } from './sesion-contexto'

/**
 * Puerta de entrada de la app: sin sesión muestra la pantalla para entrar (o para crear la primera cuenta);
 * con sesión, muestra la app. Si la sesión vence mientras se usa, vuelve a pedir la contraseña
 * y, al entrar, sigue en la misma pantalla.
 */
export function SesionProvider({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<EstadoSesion>()
  const [error, setError] = useState<string | null>(null)

  const cargar = useCallback(() => {
    apiSesion
      .estado()
      .then((e) => {
        setEstado(e)
        setError(null)
      })
      .catch((e: unknown) => setError(mensajeDe(e)))
  }, [])

  useEffect(cargar, [cargar])

  useEffect(() => {
    window.addEventListener(EVENTO_SESION_VENCIDA, cargar)
    return () => window.removeEventListener(EVENTO_SESION_VENCIDA, cargar)
  }, [cargar])

  const negocio = nombreVisible(estado?.negocio)
  useEffect(() => {
    document.title = negocio
  }, [negocio])

  const salir = useCallback(async () => {
    try {
      await apiSesion.salir()
    } finally {
      cargar()
    }
  }, [cargar])

  const setNegocio = useCallback((nombre: string) => {
    setEstado((actual) => (actual ? { ...actual, negocio: nombre } : actual))
  }, [])

  if (!estado) {
    return error ? (
      <div className="mx-auto max-w-md px-4 pt-24 text-center">
        <p className="mb-4 text-slate-700">{error}</p>
        <Boton onClick={cargar}>Reintentar</Boton>
      </div>
    ) : (
      <Cargando texto="Abriendo…" />
    )
  }

  if (!estado.autenticado) {
    return estado.requiereConfiguracion ? (
      <CrearCuenta negocio={negocio} pideCodigo={estado.requiereCodigo} onListo={setEstado} />
    ) : (
      <Entrar negocio={negocio} onListo={setEstado} />
    )
  }

  return (
    <ContextoSesion.Provider value={{ usuario: estado.usuario ?? '', negocio, setNegocio, salir }}>
      {children}
    </ContextoSesion.Provider>
  )
}
