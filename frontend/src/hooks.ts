import { useEffect, useEffectEvent, useState } from 'react'
import { useSearchParams } from 'react-router'
import { mensajeDe } from './api/http'
import type { ConsultaPeriodo } from './api/recursos'
import type { TipoPeriodo } from './api/tipos'

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

const TIPOS_PERIODO: TipoPeriodo[] = ['MES', 'MES_ANTERIOR', 'ANIO', 'RANGO']

/**
 * Periodo del resumen guardado en la dirección (?periodo=MES, o RANGO con desde y hasta), así
 * "Más detalles" abre con el mismo periodo y el botón Atrás del navegador lo respeta.
 */
export function usePeriodo(): [ConsultaPeriodo, (consulta: ConsultaPeriodo) => void, string] {
  const [params, setParams] = useSearchParams()
  const tipo = params.get('periodo') as TipoPeriodo | null
  const periodo: TipoPeriodo = tipo && TIPOS_PERIODO.includes(tipo) ? tipo : 'MES'
  const desde = params.get('desde') ?? undefined
  const hasta = params.get('hasta') ?? undefined
  const consulta: ConsultaPeriodo =
    periodo === 'RANGO' && desde && hasta ? { periodo, desde, hasta } : { periodo: periodo === 'RANGO' ? 'MES' : periodo }
  const cambiar = (nueva: ConsultaPeriodo) => {
    const valores: Record<string, string> = { periodo: nueva.periodo }
    if (nueva.periodo === 'RANGO' && nueva.desde && nueva.hasta) {
      valores.desde = nueva.desde
      valores.hasta = nueva.hasta
    }
    setParams(valores, { replace: true })
  }
  const textoParams = new URLSearchParams(
    Object.entries(consulta).filter(([, v]) => v !== undefined) as [string, string][],
  ).toString()
  return [consulta, cambiar, textoParams]
}
