import type { PDFDocumentProxy } from 'pdfjs-dist/legacy/build/pdf.mjs'

/**
 * Abre un PDF con pdf.js (el visor de PDF de Firefox). Se carga recién al abrir el primer PDF: pesa cerca de
 * 2 MB y el resto de la app no lo necesita. Se usa la versión "legacy", la que funciona en más celulares.
 */
export async function abrirDocumentoPdf(archivo: Blob): Promise<PDFDocumentProxy> {
  const [pdfjs, trabajador] = await Promise.all([
    import('pdfjs-dist/legacy/build/pdf.mjs'),
    import('pdfjs-dist/legacy/build/pdf.worker.min.mjs?url'),
  ])
  pdfjs.GlobalWorkerOptions.workerSrc = trabajador.default
  return pdfjs.getDocument({ data: new Uint8Array(await archivo.arrayBuffer()) }).promise
}

/**
 * Escala para dibujar una página nítida en la pantalla sin pasarse de memoria: Safari en el iPhone no dibuja
 * lienzos de más de ~16 millones de píxeles (y varios grandes a la vez lo cierran).
 */
export function escalaDeDibujo(anchoPagina: number, altoPagina: number, anchoEnPantalla: number, densidad: number): number {
  const MAXIMO_PIXELES = 8_000_000
  let escala = (anchoEnPantalla / anchoPagina) * Math.min(Math.max(densidad, 1), 3)
  const pixeles = anchoPagina * altoPagina * escala * escala
  if (pixeles > MAXIMO_PIXELES) {
    escala *= Math.sqrt(MAXIMO_PIXELES / pixeles)
  }
  return escala
}
