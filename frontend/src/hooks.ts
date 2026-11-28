import { useEffect, useEffectEvent, useState } from 'react'
import { useSearchParams } from 'react-router'
import { mensajeDe } from './api/http'
import { pedidos, type ConsultaPeriodo } from './api/recursos'
import type { TipoPeriodo } from './api/tipos'
import { diaLocal, fechaDia, sumarDias } from './util/formato'
import { abreTeclado, deslizoVertical, type Punto } from './util/teclado'

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

/**
 * Un buscador cuyo texto se guarda en la dirección (?buscar=), así al volver de un detalle la lista sigue con la
 * misma búsqueda. `buscado` llega con un pequeño retraso, para no llamar a la API en cada tecla.
 */
export function useBusquedaEnUrl() {
  const [params, setParams] = useSearchParams()
  const [texto, setTexto] = useState(params.get('buscar') ?? '')
  const buscado = useRetardo(texto.trim())

  useEffect(() => {
    setParams(
      (actual) => {
        const nuevos = new URLSearchParams(actual)
        if (buscado) {
          nuevos.set('buscar', buscado)
        } else {
          nuevos.delete('buscar')
        }
        return nuevos
      },
      { replace: true },
    )
  }, [buscado, setParams])

  return { texto, setTexto, buscado }
}

/** Búsqueda y rango de fechas de una lista por días (despachados, entradas registradas). */
export interface BusquedaConFechas {
  /** Lo que está escrito en el buscador */
  texto: string
  setTexto: (texto: string) => void
  /** Lo buscado (con retraso, sin espacios a los lados) */
  buscado: string
  /** Fechas elegidas con "Elegir fechas"; null si no se eligieron */
  elegidas: { desde: string; hasta: string } | null
  cambiarFechas: (fechas: { desde: string; hasta: string } | null) => void
  /** Rango que se pide a la API (null = sin límite) */
  desde: string | null
  hasta: string | null
  hoy: string
  /** "Últimos 7 días", "Todo el historial", "El 17/11/2026" o "Del … al …" */
  periodo: string
  /** Cambia cuando cambia la búsqueda o las fechas: para volver a cargar */
  clave: string
}

/**
 * Búsqueda con fechas guardada en la dirección (?buscar=&desde=&hasta=), así al volver de un detalle la lista
 * sigue igual. Sin búsqueda ni fechas se piden los últimos 7 días; al buscar sin elegir fechas, todo el historial.
 */
export function useBusquedaConFechas(): BusquedaConFechas {
  const [params, setParams] = useSearchParams()
  const { texto, setTexto, buscado } = useBusquedaEnUrl()
  const desdeElegido = params.get('desde')
  const hastaElegido = params.get('hasta')
  const elegidas = desdeElegido !== null && hastaElegido !== null ? { desde: desdeElegido, hasta: hastaElegido } : null

  const hoy = diaLocal()
  const desde = elegidas ? elegidas.desde : buscado ? null : sumarDias(hoy, -6)
  const hasta = elegidas ? elegidas.hasta : buscado ? null : hoy

  function cambiarFechas(fechas: { desde: string; hasta: string } | null) {
    setParams(
      (actual) => {
        const nuevos = new URLSearchParams(actual)
        if (fechas) {
          nuevos.set('desde', fechas.desde)
          nuevos.set('hasta', fechas.hasta)
        } else {
          nuevos.delete('desde')
          nuevos.delete('hasta')
        }
        return nuevos
      },
      { replace: true },
    )
  }

  const periodo = elegidas
    ? elegidas.desde === elegidas.hasta
      ? `El ${fechaDia(elegidas.desde)}`
      : `Del ${fechaDia(elegidas.desde)} al ${fechaDia(elegidas.hasta)}`
    : buscado
      ? 'Todo el historial'
      : 'Últimos 7 días'

  return {
    texto,
    setTexto,
    buscado,
    elegidas,
    cambiarFechas,
    desde,
    hasta,
    hoy,
    periodo,
    clave: `${buscado}|${desde ?? ''}|${hasta ?? ''}`,
  }
}

/**
 * Lista paginada con "Ver más": la primera página se carga con useCarga y las siguientes se van sumando.
 * Lo cargado con "Ver más" solo vale para la búsqueda (clave) en la que se pidió.
 */
export function useListaConVerMas<R, T>(cargar: (pagina: number) => Promise<R>, clave: string, items: (r: R) => T[]) {
  const primera = useCarga(() => cargar(0), clave)
  const [extra, setExtra] = useState<{ clave: string; items: T[]; pagina: number }>()
  const [cargandoMas, setCargandoMas] = useState(false)
  const [errorMas, setErrorMas] = useState<string | null>(null)

  const mas = extra?.clave === clave && !primera.cargando ? extra : undefined
  const lista = [...(primera.datos ? items(primera.datos) : []), ...(mas?.items ?? [])]

  async function verMas() {
    setCargandoMas(true)
    setErrorMas(null)
    try {
      const pagina = (mas?.pagina ?? 0) + 1
      const siguiente = await cargar(pagina)
      setExtra({ clave, items: [...(mas?.items ?? []), ...items(siguiente)], pagina })
    } catch (e) {
      setErrorMas(mensajeDe(e))
    } finally {
      setCargandoMas(false)
    }
  }

  return { ...primera, lista, verMas, cargandoMas, errorMas }
}

/**
 * Qué productos ya están en "Por comprar" (agregados a mano o porque faltan para un pedido), más los que se agregan
 * desde la pantalla, para mostrar "✓ En Por comprar" en vez del botón de agregar.
 */
export function useEnPorComprar() {
  const lista = useCarga(() => pedidos.porComprar())
  const [agregados, setAgregados] = useState<Set<number>>(() => new Set())
  const ids = new Set([...(lista.datos?.productos.map((p) => p.productoId) ?? []), ...agregados])
  return {
    tiene: (productoId: number) => ids.has(productoId),
    marcar: (productoId: number) => setAgregados((antes) => new Set(antes).add(productoId)),
  }
}

/**
 * En el celular: al deslizar la pantalla hacia arriba o hacia abajo mientras se escribe, se cierra el teclado, igual
 * que con el ✓ del teclado. El campo suelta el foco y guarda lo escrito (cantidad, precio, costo…), así el total se
 * actualiza. No actúa si el dedo empieza sobre el mismo campo (para mover el cursor o seleccionar texto). En la PC no
 * hace nada: solo escucha toques de la pantalla. Se usa una sola vez, en App.
 */
export function useCerrarTecladoAlDeslizar() {
  useEffect(() => {
    let inicio: Punto | null = null

    const campoConTeclado = () => {
      const activo = document.activeElement
      return activo instanceof HTMLElement && abreTeclado(activo.tagName, activo.getAttribute('type') ?? '') ? activo : null
    }
    const alTocar = (e: TouchEvent) => {
      const campo = campoConTeclado()
      const sobreElCampo = campo !== null && e.target instanceof Node && campo.contains(e.target)
      inicio = campo && !sobreElCampo && e.touches.length === 1 ? { x: e.touches[0].clientX, y: e.touches[0].clientY } : null
    }
    const alMover = (e: TouchEvent) => {
      if (!inicio || e.touches.length !== 1) return
      if (deslizoVertical(inicio, { x: e.touches[0].clientX, y: e.touches[0].clientY })) {
        campoConTeclado()?.blur()
        inicio = null
      }
    }

    // passive: no frena el desplazamiento de la pantalla
    document.addEventListener('touchstart', alTocar, { passive: true })
    document.addEventListener('touchmove', alMover, { passive: true })
    return () => {
      document.removeEventListener('touchstart', alTocar)
      document.removeEventListener('touchmove', alMover)
    }
  }, [])
}
