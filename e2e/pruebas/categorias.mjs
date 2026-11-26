// Categorías: pantalla de administración (desde Productos) y "+ Nueva" en el formulario de producto. iPhone 14.
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')

export default async function prueba(navegador) {
  const contexto = await navegador.newContext({ ...devices['iPhone 14'], locale: 'es-PE' })
  const p = await contexto.newPage()
  p.setDefaultTimeout(15000)
  const errores = []
  p.on('pageerror', (e) => errores.push(String(e)))
  p.on('dialog', (d) => d.accept())
  // Nombres únicos en cada corrida: la base de prueba guarda lo de corridas anteriores
  const s = String(Date.now()).slice(-5)

  await entrar(p)

  // --- Administrar categorías
  await p.goto(BASE + '/productos')
  await p.getByRole('link', { name: /Categorías/ }).click()
  await p.getByRole('heading', { name: 'Categorías' }).waitFor()
  await p.getByText(/\d+ categorías/).waitFor()
  ok(`Productos → Categorías: ${await p.getByText(/\d+ categorías/).textContent()}`)
  await p.screenshot({ path: path.join(DIR, 'categorias-lista.png'), fullPage: true })

  await p.getByRole('button', { name: '+ Nueva categoría' }).click()
  await p.getByRole('dialog').getByLabel('Nombre').fill(`  Prueba   E2E ${s} categoría `)
  await p.getByRole('dialog').getByRole('button', { name: 'Crear' }).click()
  await p.getByText(`Categoría creada: "Prueba E2E ${s} categoría".`).waitFor()
  ok('crea una categoría (limpia los espacios)')

  await p.getByRole('button', { name: '+ Nueva categoría' }).click()
  await p.getByRole('dialog').getByLabel('Nombre').fill(`PRUEBA E2E ${s} CATEGORIA`)
  await p.getByRole('dialog').getByText('Ya existe la categoría').waitFor()
  if (!(await p.getByRole('dialog').getByRole('button', { name: 'Crear' }).isDisabled())) throw new Error('debería impedir el duplicado')
  await p.screenshot({ path: path.join(DIR, 'categorias-repetida.png') })
  ok('no deja crear una repetida aunque cambien mayúsculas y tildes')
  await p.getByRole('dialog').getByRole('button', { name: 'Cancelar' }).click()

  await p.getByPlaceholder('Buscar categoría…').fill(`prueba e2e ${s}`)
  await p.getByRole('button', { name: `Renombrar Prueba E2E ${s} categoría` }).click()
  await p.getByRole('dialog').getByLabel('Nombre').fill(`PRUEBA E2E ${s} CATEGORÍA`)
  await p.getByRole('dialog').getByRole('button', { name: 'Guardar' }).click()
  await p.getByText(`Categoría renombrada: "PRUEBA E2E ${s} CATEGORÍA".`).waitFor()
  ok('renombra (se puede pasar a mayúsculas)')

  await p.getByRole('button', { name: `Eliminar PRUEBA E2E ${s} CATEGORÍA` }).click()
  await p.getByText(`Se eliminó la categoría "PRUEBA E2E ${s} CATEGORÍA".`).waitFor()
  ok('elimina una categoría sin productos')

  await p.getByPlaceholder('Buscar categoría…').fill('guantes')
  await p.getByText(/\d+ productos?/).first().waitFor()
  if (await p.getByRole('button', { name: 'Eliminar Guantes' }).count()) throw new Error('no debería poder eliminar Guantes')
  ok(`una categoría con productos no muestra el botón de eliminar (${await p.getByText(/\d+ productos?/).first().textContent()})`)

  // --- "+ Nueva" en el formulario de producto
  await p.goto(BASE + '/productos/nuevo')
  await p.getByRole('button', { name: '+ Nueva' }).click()
  await p.getByRole('dialog').getByLabel('Nombre').fill('guantes')
  await p.getByRole('button', { name: 'Usar «Guantes»' }).click()
  const elegida = await p.locator('#categoria-producto option:checked').textContent()
  if (elegida !== 'Guantes') throw new Error('debería elegir Guantes, eligió ' + elegida)
  ok('si ya existe, ofrece usar esa y la deja elegida')

  await p.getByRole('button', { name: '+ Nueva' }).click()
  await p.getByRole('dialog').getByLabel('Nombre').fill(`Prueba E2E ${s} desde producto`)
  await p.screenshot({ path: path.join(DIR, 'categorias-desde-producto.png') })
  await p.getByRole('dialog').getByRole('button', { name: 'Crear' }).click()
  await p.getByRole('dialog').waitFor({ state: 'detached' })
  const nueva = await p.locator('#categoria-producto option:checked').textContent()
  if (nueva !== `Prueba E2E ${s} desde producto`) throw new Error('debería quedar elegida la nueva, quedó ' + nueva)
  ok('crea la categoría desde el formulario y queda elegida')

  await p.getByLabel('Nombre del producto').fill(`Producto de prueba E2E ${s} categorías`)
  await p.getByLabel('Marca o laboratorio').fill('MARCA E2E')
  await p.getByLabel('Presentación').fill('Caja x 10')
  await p.getByRole('button', { name: 'Guardar producto' }).click()
  await p.waitForURL((u) => !u.pathname.endsWith('/nuevo'))
  ok('guarda el producto en la categoría nueva')

  await p.goto(BASE + '/productos/categorias')
  await p.getByPlaceholder('Buscar categoría…').fill(`${s} desde producto`)
  await p.getByText('1 producto', { exact: true }).waitFor()
  if (await p.getByRole('button', { name: `Eliminar Prueba E2E ${s} desde producto` }).count()) throw new Error('ya no debería poder eliminarse')
  ok('la categoría nueva cuenta 1 producto y ya no se puede eliminar')

  // PC: el botón Categorías se ve en la barra
  const pc = await navegador.newContext({ viewport: { width: 1280, height: 800 }, locale: 'es-PE', storageState: await contexto.storageState() })
  const p2 = await pc.newPage()
  await p2.goto(BASE + '/productos')
  await p2.getByRole('link', { name: /Categorías/ }).waitFor()
  await p2.screenshot({ path: path.join(DIR, 'categorias-productos-pc.png') })
  ok('en la PC el botón Categorías aparece arriba a la derecha de Productos')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
