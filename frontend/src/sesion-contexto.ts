import { createContext, useContext } from 'react'

export interface Sesion {
  usuario: string
  /** Nombre del negocio para el encabezado (el que se guarda en Configuración) */
  negocio: string
  /** Actualiza el nombre mostrado tras cambiarlo en Configuración */
  setNegocio: (nombre: string) => void
  salir: () => Promise<void>
}

export const ContextoSesion = createContext<Sesion | null>(null)

/** Datos de la sesión iniciada. Solo se usa dentro de las pantallas protegidas. */
export function useSesion(): Sesion {
  const sesion = useContext(ContextoSesion)
  if (!sesion) {
    throw new Error('useSesion debe usarse dentro de <SesionProvider>')
  }
  return sesion
}

const NOMBRE_GENERICO = 'Insumos Médicos'

/**
 * El nombre del negocio vive en la base de datos (Configuración), no en el código: el repositorio es público.
 * Mientras no se haya configurado, se muestra un nombre genérico.
 */
export function nombreVisible(negocio: string | null | undefined): string {
  const limpio = negocio?.trim() ?? ''
  return limpio === '' || limpio.startsWith('[') ? NOMBRE_GENERICO : limpio
}
