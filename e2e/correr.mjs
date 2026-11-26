// Corre las pruebas de navegador una por una, cada una con su propio navegador.
//   node correr.mjs              → todas
//   node correr.mjs lotes pdf    → solo las que tengan "lotes" o "pdf" en el nombre
// Si una falla, guarda una captura de cada pantalla abierta en la carpeta de resultados.
import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { chromium } from 'playwright-core'
import { BASE, CANAL, CAPTURAS, cerrarSql, esperarApp } from './util.mjs'

const carpeta = path.join(import.meta.dirname, 'pruebas')
const filtros = process.argv.slice(2)
const archivos = fs
  .readdirSync(carpeta)
  .filter((f) => f.endsWith('.mjs'))
  .filter((f) => filtros.length === 0 || filtros.some((t) => f.includes(t)))
  .sort()

console.log(`Pruebas de navegador en ${BASE} con ${CANAL}: ${archivos.length}`)
await esperarApp()

const fallas = []
const inicioTotal = performance.now()
for (const archivo of archivos) {
  const nombre = archivo.replace('.mjs', '')
  console.log(`\n▶ ${nombre}`)
  const navegador = await chromium.launch({ channel: CANAL, headless: true })
  const inicio = performance.now()
  try {
    const { default: prueba } = await import(pathToFileURL(path.join(carpeta, archivo)).href)
    await prueba(navegador)
    console.log(`  (${((performance.now() - inicio) / 1000).toFixed(1)} s)`)
  } catch (e) {
    fallas.push(nombre)
    console.error(`  ✗ FALLÓ: ${e.message}`)
    const paginas = navegador.contexts().flatMap((c) => c.pages())
    for (const [i, pagina] of paginas.entries()) {
      await pagina.screenshot({ path: path.join(CAPTURAS, `FALLO-${nombre}-${i + 1}.png`), fullPage: true }).catch(() => {})
    }
  } finally {
    await navegador.close()
  }
}
await cerrarSql()

const minutos = ((performance.now() - inicioTotal) / 60000).toFixed(1)
if (fallas.length) {
  console.error(`\n✗ ${fallas.length} de ${archivos.length} fallaron (${minutos} min): ${fallas.join(', ')}`)
  process.exitCode = 1
} else {
  console.log(archivos.length === 1 ? `\n✓ La prueba pasó (${minutos} min)` : `\n✓ Las ${archivos.length} pruebas pasaron (${minutos} min)`)
}
