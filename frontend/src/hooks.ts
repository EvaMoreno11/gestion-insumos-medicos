import { useEffect, useEffectEvent, useState } from 'react'
import { mensajeDe } from './api/http'

interface Carga<T> {
  /** Último resultado recibido. Mientras llega uno nuevo se conserva el anterior (la lista no parpadea). */
  datos: T | undefined
  cargando: boolean
  error: string | null
}

interface Resultado<T> {
  clave: string | number
  datos?: T
  error?: string
}

/**
 * Carga datos de la API al abrir la pantalla y cada vez que cambia la clave
 * (el texto buscado, el id de la ruta...).
 *
 * "cargando" no es un estado aparte: se calcula comparando la clave actual con la del
 * último resultado. Así, si llegan respuestas desordenadas al escribir rápido, solo vale la última.
 */
export function useCarga<T>(cargar: () => Promise<T>, clave: string | number = ''): Carga<T> {
  const [resultado, setResultado] = useState<Resultado<T>>()
  const pedir = useEffectEvent(cargar)

  useEffect(() => {
    let vigente = true
    pedir()
      .then((datos) => {
        if (vigente) {
          setResultado({ clave, datos })
        }
      })
      .catch((e: unknown) => {
        if (vigente) {
          setResultado((anterior) => ({ clave, datos: anterior?.datos, error: mensajeDe(e) }))
        }
      })
    return () => {
      vigente = false
    }
  }, [clave])

  const alDia = resultado?.clave === clave
  return {
    datos: resultado?.datos,
    cargando: !alDia,
    error: alDia ? (resultado.error ?? null) : null,
  }
}

/** Devuelve el valor con retraso: evita llamar a la API en cada tecla de un buscador. */
export function useRetardo<T>(valor: T, milisegundos = 300): T {
  const [retrasado, setRetrasado] = useState(valor)
  useEffect(() => {
    const temporizador = setTimeout(() => setRetrasado(valor), milisegundos)
    return () => clearTimeout(temporizador)
  }, [valor, milisegundos])
  return retrasado
}
