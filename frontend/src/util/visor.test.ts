import { describe, expect, it } from 'vitest'
import { hora, nombreDelDia } from './formato'
import { escalaDeDibujo } from './pdf'
import { scriptPrincipal } from './version'

describe('versión nueva de la app', () => {
  it('encuentra el archivo principal en el index.html publicado', () => {
    const html = `<!doctype html><html><head>
      <link rel="manifest" href="/manifest.webmanifest" />
      <script type="module" crossorigin src="/assets/index-BD7C7c_j.js"></script>
      <link rel="stylesheet" crossorigin href="/assets/index-BhZacfab.css">
    </head><body><div id="root"></div></body></html>`
    expect(scriptPrincipal(html)).toBe('/assets/index-BD7C7c_j.js')
  })

  it('en desarrollo (sin archivos publicados) no encuentra nada', () => {
    expect(scriptPrincipal('<script type="module" src="/src/main.tsx"></script>')).toBeNull()
  })
})

describe('últimas entradas', () => {
  it('muestra la hora y agrupa por día', () => {
    const hoy = new Date(2026, 10, 19, 18, 0)
    const local = (dia: number, h: number, m: number) => new Date(2026, 10, dia, h, m).toISOString()
    expect(hora(local(19, 9, 5))).toBe('09:05')
    expect(nombreDelDia(local(19, 9, 5), hoy)).toBe('Hoy')
    expect(nombreDelDia(local(18, 23, 59), hoy)).toBe('Ayer')
    expect(nombreDelDia(local(17, 10, 0), hoy)).toBe('17/11/2026')
  })
})

describe('vista previa del PDF', () => {
  // Página A4: 595 × 842 puntos
  it('dibuja nítido según la densidad de la pantalla', () => {
    expect(escalaDeDibujo(595, 842, 360, 2)).toBeCloseTo((360 / 595) * 2)
    expect(escalaDeDibujo(595, 842, 360, 5)).toBeCloseTo((360 / 595) * 3) // máximo 3
  })

  it('al acercar mucho, no pasa de 8 millones de píxeles por página (límite de Safari)', () => {
    const escala = escalaDeDibujo(595, 842, 1080, 3)
    expect(595 * 842 * escala * escala).toBeLessThanOrEqual(8_000_000 + 1)
    expect(escala).toBeLessThan((1080 / 595) * 3)
  })
})
