/**
 * Entrega un archivo al usuario.
 * - En el celular abre el menú de compartir (WhatsApp, Archivos, Imprimir...).
 * - En la PC lo descarga a la carpeta de Descargas.
 */
export async function compartirODescargar(contenido: Blob, nombre: string): Promise<void> {
  const archivo = new File([contenido], nombre, { type: contenido.type })
  const esTactil = window.matchMedia('(pointer: coarse)').matches

  if (esTactil && navigator.canShare?.({ files: [archivo] })) {
    try {
      await navigator.share({ files: [archivo], title: nombre })
      return
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return // el usuario cerró el menú de compartir
      }
      // si compartir falla por otra razón, se descarga
    }
  }

  const url = URL.createObjectURL(contenido)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
