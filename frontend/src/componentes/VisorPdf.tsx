import { useEffect, useEffectEvent, useRef, useState } from 'react'
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist/legacy/build/pdf.mjs'
import { compartirODescargar } from '../util/archivos'
import { abrirDocumentoPdf, escalaDeDibujo } from '../util/pdf'
import { Boton } from './basicos'

const ACERCAMIENTOS = [1, 1.5, 2, 3]

interface Props {
  archivo: Blob
  /** Nombre del archivo al compartirlo, p. ej. "nota-pedido-0002-000015.pdf" */
  nombre: string
  titulo: string
  onCerrar: () => void
  /** Si este celular no puede mostrar el PDF (navegador muy antiguo) */
  onFallo: () => void
}

/**
 * Vista previa de un PDF dentro de la app, como en WhatsApp: se ve el documento completo y desde ahí se
 * comparte (WhatsApp, Imprimir, Guardar en Archivos). Solo se dibujan las páginas cercanas a la pantalla,
 * así un catálogo de muchas páginas no agota la memoria del celular.
 */
export function VisorPdf({ archivo, nombre, titulo, onCerrar, onFallo }: Props) {
  const [documento, setDocumento] = useState<PDFDocumentProxy | null>(null)
  const [proporciones, setProporciones] = useState<number[]>([])
  const [acercamiento, setAcercamiento] = useState(0)
  const [ancho, setAncho] = useState(0)
  const [area, setArea] = useState<HTMLDivElement | null>(null)
  const avisarFallo = useEffectEvent(onFallo)
  const cerrar = useEffectEvent(onCerrar)

  // Abre el documento y mide sus páginas (alto / ancho de cada una)
  useEffect(() => {
    let vigente = true
    let abierto: PDFDocumentProxy | null = null
    abrirDocumentoPdf(archivo)
      .then(async (doc) => {
        abierto = doc
        const medidas: number[] = []
        for (let n = 1; n <= doc.numPages; n++) {
          const vista = (await doc.getPage(n)).getViewport({ scale: 1 })
          medidas.push(vista.height / vista.width)
        }
        if (vigente) {
          setProporciones(medidas)
          setDocumento(doc)
        }
      })
      .catch(() => {
        if (vigente) {
          avisarFallo()
        }
      })
    return () => {
      vigente = false
      void abierto?.loadingTask.destroy()
    }
  }, [archivo])

  // Ancho disponible para las páginas (cambia al girar el celular)
  useEffect(() => {
    if (!area) {
      return
    }
    const medir = () => setAncho(Math.min(area.clientWidth - 24, 900))
    medir()
    const observador = new ResizeObserver(medir)
    observador.observe(area)
    return () => observador.disconnect()
  }, [area])

  // Mientras está abierto, la pantalla de atrás no se desplaza; Escape lo cierra
  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const tecla = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        cerrar()
      }
    }
    window.addEventListener('keydown', tecla)
    return () => {
      document.body.style.overflow = anterior
      window.removeEventListener('keydown', tecla)
    }
  }, [])

  const factor = ACERCAMIENTOS[acercamiento]

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-slate-700" role="dialog" aria-modal="true" aria-label={titulo}>
      <header className="flex items-center gap-1 bg-slate-900 pt-[env(safe-area-inset-top)] pr-4 text-white">
        <button
          type="button"
          onClick={onCerrar}
          className="flex h-12 w-12 shrink-0 items-center justify-center text-3xl leading-none active:bg-white/15"
          aria-label="Cerrar"
        >
          ×
        </button>
        <h2 className="min-w-0 flex-1 truncate font-semibold">{titulo}</h2>
        {proporciones.length > 1 && <span className="shrink-0 text-sm text-slate-300">{proporciones.length} páginas</span>}
      </header>

      <div ref={setArea} className="flex-1 overflow-auto overscroll-contain px-3 py-4">
        {!documento && <p className="pt-10 text-center text-slate-200">Abriendo el PDF…</p>}
        {documento &&
          ancho > 0 &&
          proporciones.map((proporcion, i) => (
            <PaginaPdf
              key={i}
              documento={documento}
              numero={i + 1}
              ancho={ancho * factor}
              proporcion={proporcion}
              raiz={area}
            />
          ))}
      </div>

      <footer className="flex items-center gap-2 bg-white px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
        <Boton
          variante="secundario"
          className="w-12 px-0 text-2xl"
          disabled={acercamiento === 0}
          onClick={() => setAcercamiento((a) => a - 1)}
          aria-label="Alejar"
        >
          −
        </Boton>
        <span className="w-12 text-center text-sm text-slate-600 tabular-nums">{Math.round(factor * 100)} %</span>
        <Boton
          variante="secundario"
          className="w-12 px-0 text-2xl"
          disabled={acercamiento === ACERCAMIENTOS.length - 1}
          onClick={() => setAcercamiento((a) => a + 1)}
          aria-label="Acercar"
        >
          +
        </Boton>
        <Boton className="flex-1" onClick={() => void compartirODescargar(archivo, nombre)}>
          Compartir
        </Boton>
      </footer>
    </div>
  )
}

/** Una página: ocupa su lugar siempre, pero solo se dibuja cuando está cerca de la pantalla. */
function PaginaPdf({
  documento,
  numero,
  ancho,
  proporcion,
  raiz,
}: {
  documento: PDFDocumentProxy
  numero: number
  ancho: number
  proporcion: number
  raiz: HTMLDivElement | null
}) {
  const caja = useRef<HTMLDivElement>(null)
  const mostrado = useRef<HTMLCanvasElement | null>(null)
  const [cerca, setCerca] = useState(numero === 1)

  useEffect(() => {
    const elemento = caja.current
    if (!elemento) {
      return
    }
    const observador = new IntersectionObserver(([entrada]) => setCerca(entrada.isIntersecting), {
      root: raiz,
      rootMargin: '100% 0px',
    })
    observador.observe(elemento)
    return () => observador.disconnect()
  }, [raiz])

  useEffect(() => {
    // Lejos de la pantalla: se suelta el dibujo para liberar memoria
    const liberar = () => {
      if (mostrado.current) {
        mostrado.current.width = 0
        mostrado.current.height = 0
        mostrado.current.remove()
        mostrado.current = null
      }
    }
    if (!cerca) {
      liberar()
      return
    }
    let cancelado = false
    let tarea: RenderTask | null = null
    const lienzo = document.createElement('canvas')
    lienzo.className = 'block h-full w-full'
    documento
      .getPage(numero)
      .then((pagina) => {
        if (cancelado) {
          return
        }
        const base = pagina.getViewport({ scale: 1 })
        const vista = pagina.getViewport({
          scale: escalaDeDibujo(base.width, base.height, ancho, window.devicePixelRatio || 1),
        })
        lienzo.width = Math.floor(vista.width)
        lienzo.height = Math.floor(vista.height)
        tarea = pagina.render({ canvas: lienzo, viewport: vista })
        return tarea.promise.then(() => {
          if (cancelado || !caja.current) {
            return
          }
          // Se cambia el dibujo anterior por el nuevo recién cuando está listo (sin parpadeo al acercar)
          const anterior = mostrado.current
          caja.current.replaceChildren(lienzo)
          mostrado.current = lienzo
          if (anterior) {
            anterior.width = 0
            anterior.height = 0
          }
        })
      })
      .catch(() => {
        // Dibujo cancelado (se alejó o cambió el acercamiento): no es un error
      })
    return () => {
      cancelado = true
      tarea?.cancel()
      if (mostrado.current !== lienzo) {
        lienzo.width = 0
        lienzo.height = 0
      }
    }
  }, [cerca, ancho, documento, numero])

  useEffect(
    () => () => {
      if (mostrado.current) {
        mostrado.current.width = 0
        mostrado.current.height = 0
      }
    },
    [],
  )

  return (
    <div
      ref={caja}
      className="mx-auto mb-3 bg-white shadow-lg"
      style={{ width: ancho, height: ancho * proporcion }}
      role="img"
      aria-label={`Página ${numero}`}
    />
  )
}
