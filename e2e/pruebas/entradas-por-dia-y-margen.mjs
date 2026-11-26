// Entradas registradas desplegables (3 por día) y margen en los pedidos (tarjetas y pie, también a 320 px).
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')
const igual = (real, esperado, que) => {
  if (real !== esperado) throw new Error(`${que}: «${real}» (esperaba «${esperado}»)`)
}

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
  const producto = (nombre, costo) =>
    api('/api/productos', 'POST', { nombre, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja', unidadMedida: 'Caja', costo })
  const entradas = async (prod, n) => {
    for (let i = 0; i < n; i++) {
      await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `D${s}-${i}`, cantidad: 1, costo: 2 })
    }
  }

  // ------------------------------------------------------------------ entradas desplegables
  const cinco = await producto(`VENDA CINCO E2E${s}`, 2)
  await entradas(cinco, 5)
  await p.goto(`${BASE}/entrada?pestana=registradas`)
  const buscador = p.getByPlaceholder('Buscar producto o N° de lote…')
  const tarjetas = (nombre) => p.getByRole('link').filter({ hasText: nombre })
  const buscar = async (texto, n) => {
    await buscador.fill(texto)
    await p.getByRole('status').filter({ hasText: `«${texto}» · ${n} entradas` }).waitFor()
  }
  await buscar(`venda cinco e2e${s}`, 5)
  igual(await tarjetas(`VENDA CINCO E2E${s}`).count(), 3, 'visibles al entrar')
  const titulo = await p.getByRole('heading', { name: /^Hoy/ }).innerText()
  if (!titulo.includes('5 entradas · S/ 10.00')) throw new Error('título: ' + titulo)
  await p.screenshot({ path: path.join(DIR, 'entradas-dia-cerrado.png') })
  await p.getByRole('button', { name: 'Ver 2 más de hoy ▾' }).click()
  igual(await tarjetas(`VENDA CINCO E2E${s}`).count(), 5, 'visibles al desplegar')
  await p.getByRole('button', { name: 'Ver menos ▴' }).click()
  igual(await tarjetas(`VENDA CINCO E2E${s}`).count(), 3, 'visibles al plegar')
  ok('un día con 5 entradas muestra 3 y "Ver 2 más de hoy ▾"; despliega las 5 y "Ver menos ▴" vuelve a 3')

  // 53 entradas de hoy: la lista carga 50 y, al desplegar, se trae el día completo
  const muchas = await producto(`VENDA MUCHAS E2E${s}`, 2)
  await entradas(muchas, 53)
  await buscar(`venda muchas e2e${s}`, 53)
  igual(await tarjetas(`VENDA MUCHAS E2E${s}`).count(), 3, 'visibles con 53')
  await p.getByRole('button', { name: 'Ver 50 más de hoy ▾' }).click()
  await p.getByRole('button', { name: 'Ver menos ▴' }).waitFor()
  igual(await tarjetas(`VENDA MUCHAS E2E${s}`).count(), 53, 'visibles al desplegar el día cortado')
  ok('con 53 entradas en un día (la lista carga de 50 en 50), "Ver 50 más de hoy" trae y muestra las 53')

  // Al cambiar la búsqueda, el día vuelve a verse cerrado
  await buscar(`venda cinco e2e${s}`, 5)
  igual(await tarjetas(`VENDA CINCO E2E${s}`).count(), 3, 'al cambiar la búsqueda')
  ok('al cambiar la búsqueda, los días vuelven a verse cerrados')

  // ------------------------------------------------------------------ margen en los pedidos
  const jeringa = await producto(`JERINGA MARGEN E2E${s}`, 10)
  await api('/api/movimientos/entrada', 'POST', { productoId: jeringa.id, numeroLote: `M${s}`, cantidad: 100, costo: 10 })
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Margen E2E${s}`, porcentaje: 15, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  const conItem = await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: jeringa.id, cantidad: 4 })
  await api(`/api/pedidos/${pedido.id}/items/${conItem.items[0].id}`, 'PUT', { cantidad: 4, precio: 11.5 })
  // 4 × 11.50 = 46; costo 40; gana 6 → 13 %

  await p.goto(`${BASE}/pedidos`)
  const tarjeta = p.getByRole('link').filter({ hasText: `Botica Margen E2E${s}` })
  await tarjeta.waitFor()
  if (!(await tarjeta.innerText()).includes('gana S/ 6.00 · 13 %')) throw new Error('pendiente: ' + (await tarjeta.innerText()))
  ok('Pendientes: la tarjeta dice "gana S/ 6.00 · 13 %"')

  await tarjeta.click()
  const pie = p.locator('footer')
  await pie.getByText('gana S/ 6.00 · 13 %').waitFor()
  await p.screenshot({ path: path.join(DIR, 'margen-borrador.png') })
  ok('mientras se arma el pedido (borrador), el pie dice "costo S/ 40.00 · gana S/ 6.00 · 13 %"')

  const angosto = await navegador.newContext({ ...devices['iPhone SE'], locale: 'es-PE', storageState: await contexto.storageState() })
  const q = await angosto.newPage()
  q.on('pageerror', (e) => errores.push(String(e)))
  await q.goto(`${BASE}/pedidos/${pedido.id}`)
  await q.locator('footer').getByText('gana S/ 6.00 · 13 %').waitFor()
  // La fila del costo y el total (la de los botones ya era así antes y no es parte de este cambio)
  const seSale = await q.evaluate(() => {
    const fila = document.querySelector('footer > div > div > div:first-child')
    return [fila, ...fila.querySelectorAll('div')].some((d) => d.scrollWidth > d.clientWidth + 1)
      || fila.getBoundingClientRect().right > window.innerWidth
  })
  igual(seSale, false, 'el pie a 320 px se sale')
  await q.screenshot({ path: path.join(DIR, 'margen-pie-320.png') })
  ok('en un celular de 320 px el pie no se corta: costo y total se acomodan en dos filas')

  await api(`/api/pedidos/${pedido.id}/despachar`, 'POST')
  await p.goto(`${BASE}/pedidos?estado=DESPACHADO`)
  await p.getByRole('link').filter({ hasText: `Botica Margen E2E${s}` }).getByText('gana S/ 6.00 · 13 %').waitFor()
  await p.screenshot({ path: path.join(DIR, 'margen-despachados.png') })
  ok('Despachados: la tarjeta dice "gana S/ 6.00 · 13 %" con el costo real de los lotes')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
