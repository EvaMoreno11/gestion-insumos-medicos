// Vista previa del PDF de la nota y del catálogo (iPhone: dentro de la app; PC: otra pestaña).
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')

export default async function prueba(navegador) {
  const contexto = await navegador.newContext({ ...devices['iPhone 14'], locale: 'es-PE', acceptDownloads: true })
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
  ok(`en el celular emulado, pointer coarse = ${await p.evaluate(() => matchMedia('(pointer: coarse)').matches)}`)

  // Datos: un producto con dos entradas y un pedido
  const categoriaId = (await api('/api/categorias'))[0].id
  const producto = await api('/api/productos', 'POST', {
    nombre: `ALCOHOL ETÍLICO 70° E2E${s} X 1 L`, marca: 'MARCA E2E', categoriaId, presentacion: 'Frasco', unidadMedida: 'Frasco', costo: 8.5,
  })
  await api('/api/movimientos/entrada', 'POST', { productoId: producto.id, numeroLote: `A${s}`, fechaVencimiento: '2028-03-31', cantidad: 12, costo: 8.5 })
  await api('/api/movimientos/entrada', 'POST', { productoId: producto.id, numeroLote: `B${s}`, fechaVencimiento: '2029-06-30', cantidad: 6, costo: 9.1, nota: 'Llegó con la factura 123' })
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Visor E2E ${s}`, porcentaje: 15, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: producto.id, cantidad: 5 })

  // ---------------------------------------------------------------- vista previa en el celular
  await p.goto(`${BASE}/pedidos/${pedido.id}`)
  await p.getByRole('button', { name: 'PDF', exact: true }).click()
  const visor = p.getByRole('dialog', { name: 'Pedido en borrador' })
  await visor.waitFor()
  const lienzo = visor.locator('canvas').first()
  await lienzo.waitFor()
  const dibujado = await lienzo.evaluate((c) => {
    const ctx = c.getContext('2d')
    const datos = ctx.getImageData(0, 0, c.width, c.height).data
    let oscuros = 0
    for (let i = 0; i < datos.length; i += 4 * 97) if (datos[i] < 128) oscuros++
    return { ancho: c.width, alto: c.height, oscuros }
  })
  if (dibujado.oscuros < 50) throw new Error('la página parece vacía: ' + JSON.stringify(dibujado))
  await p.screenshot({ path: path.join(DIR, 'visor-nota-celular.png') })
  ok(`PDF → vista previa dentro de la app, con la nota dibujada (${dibujado.ancho}×${dibujado.alto} px)`)

  await visor.getByRole('button', { name: 'Acercar' }).click()
  await visor.getByText('150 %').waitFor()
  await p.waitForTimeout(800)
  const anchoAcercado = await visor.getByRole('img', { name: 'Página 1' }).evaluate((e) => e.getBoundingClientRect().width)
  await p.screenshot({ path: path.join(DIR, 'visor-nota-acercada.png') })
  ok(`Acercar: la página pasa a 150 % (${Math.round(anchoAcercado)} px de ancho, se desplaza hacia los lados)`)

  // Menú de compartir simulado (en el iPhone es el de WhatsApp, Imprimir, Archivos…)
  await p.evaluate(() => {
    window.__compartido = null
    navigator.canShare = () => true
    navigator.share = async (datos) => {
      window.__compartido = datos.files.map((f) => `${f.name} (${f.type}, ${f.size} bytes)`)
    }
  })
  await visor.getByRole('button', { name: 'Compartir' }).click()
  await p.waitForFunction(() => window.__compartido !== null)
  ok(`Compartir abre el menú de compartir con ${await p.evaluate(() => window.__compartido.join(', '))}`)

  await visor.getByRole('button', { name: 'Cerrar' }).click()
  await visor.waitFor({ state: 'detached' })
  ok('Cerrar vuelve al pedido')

  await p.goto(`${BASE}/inventario/descargar`)
  await p.getByRole('button', { name: 'PDF', exact: true }).click()
  const catalogo = p.getByRole('dialog', { name: 'Catálogo' })
  await catalogo.locator('canvas').first().waitFor()
  const paginas = await catalogo.getByRole('img').count()
  await p.screenshot({ path: path.join(DIR, 'visor-catalogo-celular.png') })
  ok(`el catálogo PDF también se abre en la vista previa (${paginas} página${paginas === 1 ? '' : 's'})`)
  await catalogo.getByRole('button', { name: 'Cerrar' }).click()

  // ---------------------------------------------------------------- en la PC: otra pestaña
  const pc = await navegador.newContext({ viewport: { width: 1280, height: 800 }, locale: 'es-PE', storageState: await contexto.storageState() })
  const p2 = await pc.newPage()
  await p2.goto(`${BASE}/pedidos/${pedido.id}`)
  const [pestana] = await Promise.all([pc.waitForEvent('page'), p2.getByRole('button', { name: 'PDF', exact: true }).click()])
  await pestana.waitForLoadState('domcontentloaded').catch(() => {})
  if (!pestana.url().endsWith(`/api/pedidos/${pedido.id}/nota.pdf`)) throw new Error('pestaña: ' + pestana.url())
  if (await p2.getByRole('dialog').count()) throw new Error('en la PC no debería abrir el visor interno')
  const cabecera = await p2.evaluate(async (id) => (await fetch(`/api/pedidos/${id}/nota.pdf`)).headers.get('content-disposition'), pedido.id)
  ok(`en la PC, PDF abre otra pestaña con ${pestana.url().replace(BASE, '')} (${cabecera})`)

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
