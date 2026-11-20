import { useState, type ReactNode } from 'react'
import { construirUrl, descargar } from '../api/http'
import { compartirODescargar, esPantallaTactil } from '../util/archivos'
import { VisorPdf } from './VisorPdf'

interface Abierto {
  archivo: Blob
  nombre: string
  titulo: string
}

/**
 * Abrir un PDF de la API para verlo:
 * - En el celular: vista previa dentro de la app, con el botón Compartir (WhatsApp, Imprimir, Archivos).
 * - En la PC: en otra pestaña del navegador, que ya trae Imprimir y Descargar.
 *
 * Devuelve `abrir` y el `visor`, que la pantalla debe dibujar (no muestra nada mientras está cerrado).
 */
export function useVisorPdf(): {
  abrir: (ruta: string, nombre: string, titulo: string) => Promise<void>
  visor: ReactNode
} {
  const [abierto, setAbierto] = useState<Abierto | null>(null)

  async function abrir(ruta: string, nombre: string, titulo: string) {
    if (!esPantallaTactil()) {
      // Se abre en el mismo clic (si se espera la descarga, el navegador lo bloquea como ventana emergente)
      window.open(construirUrl(ruta), '_blank', 'noopener')
      return
    }
    setAbierto({ archivo: await descargar(ruta), nombre, titulo })
  }

  const visor = abierto && (
    <VisorPdf
      archivo={abierto.archivo}
      nombre={abierto.nombre}
      titulo={abierto.titulo}
      onCerrar={() => setAbierto(null)}
      onFallo={() => {
        // Este celular no puede mostrar la vista previa: se comparte directo, como antes
        setAbierto(null)
        void compartirODescargar(abierto.archivo, abierto.nombre)
      }}
    />
  )

  return { abrir, visor }
}
