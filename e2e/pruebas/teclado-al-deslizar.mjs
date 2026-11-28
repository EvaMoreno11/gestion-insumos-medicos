// En el celular, deslizar la pantalla cierra el teclado (como el ✓) y guarda lo escrito; en la PC no cambia nada.
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')
const igual = (real, esperado, que) => {
  if (real !== esperado) throw new Error(`${que}: «${real}» (esperaba «${esperado}»)`)
}

/** Desliza un dedo de un punto a otro con eventos táctiles reales (como en el iPhone). */
async function deslizar(pagina, desde, hasta) {
  const cdp = await pagina.context().newCDPSession(pagina)
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [desde] })
  for (let i = 1; i <= 6; i++) {
    const punto = { x: desde.x + ((hasta.x - desde.x) * i) / 6, y: desde.y + ((hasta.y - desde.y) * i) / 6 }
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [punto] })
  }
  await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
  await cdp.detach()
}

/** Centro de un elemento en la pantalla (lo trae a la vista primero). */
async function centro(elemento) {
  await elemento.scrollIntoViewIfNeeded()
  const caja = await elemento.boundingBox()
  return { x: caja.x + caja.width / 2, y: caja.y + caja.height / 2 }
}

const conFoco = (pagina) => pagina.evaluate(() => document.activeElement?.getAttribute('aria-label') ?? document.activeElement?.tagName)

export default async function prueba(navegador) {
  const contexto = await navegador.newContext({ ...devices['iPhone 14'], locale: 'es-PE', timezoneId: 'America/Lima' })
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

  const categoriaId = (await api('/api/categorias'))[0].id
  const crear = async (nombre) => {
    const prod = await api('/api/productos', 'POST', { nombre, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja x 100', unidadMedida: 'Caja', costo: 12 })
    await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `T${s}${prod.id}`, fechaVencimiento: '2029-01-31', cantidad: 50, costo: 12 })
    return prod
  }
  const jeringa = await crear(`JERINGA TECLADO E2E${s}`)
  const venda = await crear(`VENDA TECLADO E2E${s}`)
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Teclado E2E${s}`, porcentaje: 10, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: jeringa.id, cantidad: 5 })
  const precioGuardado = async () => (await api(`/api/pedidos/${pedido.id}`)).items[0].precioUnitario

  // ------------------------------------------------------------------ precio: escribir y deslizar
  await p.goto(`${BASE}/pedidos/${pedido.id}`)
  const precio = p.getByLabel('Precio unitario').first()
  const descripcion = p.getByText(jeringa.nombre).first()
  await precio.focus()
  await precio.fill('15')
  igual(await conFoco(p), 'Precio unitario', 'el precio debería tener el teclado abierto')
  const desde = await centro(descripcion)
  await deslizar(p, desde, { x: desde.x, y: desde.y - 150 })
  igual(await conFoco(p) === 'Precio unitario', false, 'al deslizar hacia arriba se cierra el teclado')
  await p.getByText('S/ 75.00').first().waitFor()
  igual(await precioGuardado(), 15, 'precio guardado')
  ok('precio 15 + deslizar hacia arriba: se cierra el teclado, se guarda y el total pasa a S/ 75.00 (sin tocar el ✓)')

  // ------------------------------------------------------------------ deslizar hacia abajo y hacia los lados
  const cantidad = p.getByLabel('Cantidad').first()
  await cantidad.focus()
  await cantidad.fill('6')
  const lado = await centro(descripcion)
  await deslizar(p, lado, { x: lado.x + 120, y: lado.y + 20 })
  igual(await conFoco(p), 'Cantidad', 'deslizar hacia los lados no cierra el teclado')
  const abajo = await centro(descripcion)
  await deslizar(p, abajo, { x: abajo.x, y: abajo.y + 150 })
  igual(await conFoco(p) === 'Cantidad', false, 'al deslizar hacia abajo se cierra el teclado')
  await p.getByText('S/ 90.00').first().waitFor()
  ok('deslizar hacia los lados no lo cierra; hacia abajo sí, y la cantidad 6 queda guardada (S/ 90.00)')

  // ------------------------------------------------------------------ el dedo empieza sobre el mismo campo
  await precio.focus()
  const sobre = await centro(precio)
  await deslizar(p, sobre, { x: sobre.x, y: sobre.y + 80 })
  igual(await conFoco(p), 'Precio unitario', 'si el dedo empieza sobre el campo, sigue escribiendo')
  ok('si el dedo empieza sobre el mismo campo (para mover el cursor), el teclado sigue abierto')

  // ------------------------------------------------------------------ buscador: la lista de resultados se queda
  const buscador = p.getByPlaceholder('Agregar producto: jer 5ml, guantes m…')
  await buscador.focus()
  await buscador.fill(`venda teclado e2e${s}`)
  const resultado = p.getByRole('button', { name: new RegExp(`VENDA TECLADO E2E${s}`) })
  await resultado.waitFor()
  const enLista = await centro(resultado)
  await deslizar(p, enLista, { x: enLista.x, y: enLista.y - 100 })
  igual(await buscador.evaluate((e) => e === document.activeElement), false, 'el buscador debería soltar el teclado')
  await resultado.waitFor()
  await p.screenshot({ path: path.join(DIR, 'teclado-buscador-sin-teclado.png') })
  await resultado.click()
  await p.getByText('2 ítems').first().waitFor()
  ok('al deslizar sobre los resultados se cierra el teclado, la lista se queda y se puede tocar el producto (2 ítems)')

  // ------------------------------------------------------------------ PC: la rueda del mouse no cierra nada
  const pc = await (await navegador.newContext({ viewport: { width: 1366, height: 768 }, locale: 'es-PE', storageState: await contexto.storageState() })).newPage()
  await pc.goto(`${BASE}/pedidos/${pedido.id}`)
  const precioPc = pc.getByLabel('Precio unitario').first()
  await precioPc.focus()
  await pc.mouse.move(700, 400)
  await pc.mouse.wheel(0, 400)
  igual(await conFoco(pc), 'Precio unitario', 'en la PC la rueda del mouse no quita el foco')
  ok('PC: desplazar con la rueda del mouse no cambia nada')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
