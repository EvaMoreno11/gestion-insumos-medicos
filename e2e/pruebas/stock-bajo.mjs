// Alertas → Stock bajo filtrado por categoría (solo esa pestaña).
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')

export default async function prueba(navegador) {
  const contexto = await navegador.newContext({ ...devices['iPhone 14'], locale: 'es-PE' })
  const p = await contexto.newPage()
  p.setDefaultTimeout(20000)
  const errores = []
  p.on('pageerror', (e) => errores.push(String(e)))
  const s = String(Date.now()).slice(-5)

  const api = (ruta, metodo = 'GET', cuerpo) =>
    p.evaluate(
      async ({ ruta, metodo, cuerpo }) => {
        const token = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1]
        const r = await fetch(ruta, {
          method: metodo,
          headers: { 'Content-Type': 'application/json', ...(token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}) },
          body: cuerpo ? JSON.stringify(cuerpo) : undefined,
        })
        const texto = await r.text()
        if (!r.ok) throw new Error(`${metodo} ${ruta}: ${r.status} ${texto}`)
        return texto ? JSON.parse(texto) : null
      },
      { ruta, metodo, cuerpo },
    )

  await entrar(p)

  // Dos productos agotados con mínimo, en una categoría nueva
  const categoria = await api('/api/categorias', 'POST', { nombre: `Categoría E2E ${s}` })
  for (const n of [1, 2]) {
    await api('/api/productos', 'POST', {
      nombre: `PRODUCTO STOCK BAJO E2E${s}-${n}`, marca: 'MARCA E2E', categoriaId: categoria.id,
      presentacion: 'Unidad', unidadMedida: 'Unidad', stockMinimo: 5,
    })
  }
  const todos = await api('/api/alertas/stock-bajo')

  await p.goto(`${BASE}/alertas`)
  const select = p.getByRole('combobox', { name: 'Categoría' })
  await select.waitFor()
  if (await p.getByText('Productos con stock igual o menor').count()) throw new Error('el texto de explicación debería haberse quitado')
  const opciones = await select.locator('option').allInnerTexts()
  if (opciones[0] !== `Todas las categorías (${todos.length})`) throw new Error('primera opción: ' + opciones[0])
  if (!opciones.includes(`Categoría E2E ${s} (2)`)) throw new Error('falta la categoría nueva: ' + opciones.join(' | '))
  await p.getByText(`${todos.length} productos, primero los agotados`).waitFor()
  ok(`Stock bajo: sin el texto, con "${opciones[0]}" y ${opciones.length - 1} categorías (solo las que tienen stock bajo)`)

  await select.selectOption({ label: `Categoría E2E ${s} (2)` })
  await p.getByText(`2 productos en Categoría E2E ${s}, primero los agotados`).waitFor()
  const filas = await p.getByRole('listitem').filter({ hasText: 'PRODUCTO STOCK BAJO E2E' }).count()
  const total = await p.getByRole('listitem').count()
  if (filas !== 2 || total !== 2) throw new Error(`deberían verse solo los 2 de la categoría (${filas}/${total})`)
  if (!p.url().includes(`categoria=${categoria.id}`)) throw new Error('la categoría debería ir en la dirección: ' + p.url())
  await p.screenshot({ path: path.join(DIR, 'stock-bajo-categoria.png') })
  ok('al elegir la categoría se ven solo sus 2 productos ("2 productos en …")')

  await p.getByRole('listitem').first().getByRole('link').first().click()
  await p.waitForURL(/\/inventario\/\d+/)
  await p.goBack()
  await p.getByText(`2 productos en Categoría E2E ${s}`).waitFor()
  ok('al abrir un producto y volver, la categoría sigue elegida')

  await p.getByRole('tab', { name: /Por vencer/ }).click()
  await p.getByRole('tab', { name: /Por vencer/, selected: true }).waitFor()
  if (await p.getByRole('combobox', { name: 'Categoría' }).count()) throw new Error('Por vencer no lleva filtro')
  await p.getByRole('tab', { name: /Vencidos/ }).click()
  await p.getByRole('tab', { name: /Vencidos/, selected: true }).waitFor()
  if (await p.getByRole('combobox', { name: 'Categoría' }).count()) throw new Error('Vencidos no lleva filtro')
  await p.getByRole('tab', { name: /Stock bajo/ }).click()
  await p.getByText(`2 productos en Categoría E2E ${s}`).waitFor()
  ok('Por vencer y Vencidos no tienen filtro; al volver a Stock bajo, la categoría sigue')

  await select.selectOption({ index: 0 })
  await p.getByText(`${todos.length} productos, primero los agotados`).waitFor()
  ok('"Todas las categorías" vuelve a mostrar todo')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
