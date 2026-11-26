// Cobranzas: pestaña Pagados (buscador, tarjetas, volver con la búsqueda) y las pestañas en dos filas en el celular.
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
  const prod = await api('/api/productos', 'POST', { nombre: `JERINGA PAGOS E2E${s}`, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja', unidadMedida: 'Caja', costo: 10 })
  await api('/api/movimientos/entrada', 'POST', { productoId: prod.id, numeroLote: `P${s}`, cantidad: 100, costo: 10 })
  const despachado = async (nombre, cantidad) => {
    const cliente = await api('/api/clientes', 'POST', { nombre, porcentaje: 10, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
    const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
    await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: prod.id, cantidad })
    return api(`/api/pedidos/${pedido.id}/despachar`, 'POST')
  }
  const rosa = await despachado(`Botica Rosa Pagos E2E${s}`, 10) //    S/ 110
  const andes = await despachado(`Clinica Andes Pagos E2E${s}`, 5) //  S/ 55
  await api(`/api/pedidos/${rosa.id}/pagos`, 'POST', { monto: 60, medio: 'EFECTIVO' })
  await api(`/api/pedidos/${rosa.id}/pagos`, 'POST', { monto: 50, medio: 'YAPE' })
  await api(`/api/pedidos/${andes.id}/pagos`, 'POST', { monto: 55, medio: 'PLIN' })

  // ------------------------------------------------------------------ pestañas en dos filas
  await p.goto(`${BASE}/cobranzas`)
  const pestanas = p.getByRole('tab')
  await pestanas.first().waitFor()
  const filas = async () => {
    const tops = await pestanas.evaluateAll((ts) => ts.map((t) => Math.round(t.getBoundingClientRect().top)))
    return [...new Set(tops)].length
  }
  const cuantas = await pestanas.count()
  igual(await filas(), 2, `filas de pestañas en el celular (${cuantas} pestañas)`)
  await p.getByRole('tab', { name: 'Pagados' }).waitFor()
  ok(`celular: las ${cuantas} pestañas van en dos filas, con "Pagados" sin número`)

  // ------------------------------------------------------------------ Pagados
  await p.getByRole('tab', { name: 'Pagados' }).click()
  const buscador = p.getByPlaceholder('Buscar cliente o N° de nota…')
  await buscador.fill(`pagos e2e${s}`)
  const tarjeta = (nombre) => p.getByRole('link').filter({ hasText: nombre })
  await tarjeta(`Botica Rosa Pagos E2E${s}`).waitFor()
  await tarjeta(`Clinica Andes Pagos E2E${s}`).waitFor()
  const textoRosa = await tarjeta(`Botica Rosa Pagos E2E${s}`).innerText()
  if (!textoRosa.includes('S/ 110.00') || !textoRosa.includes('Pagó todo el') || !textoRosa.includes('Efectivo, Yape')) {
    throw new Error('tarjeta: ' + textoRosa)
  }
  await p.screenshot({ path: path.join(DIR, 'cobranzas-pagados.png') })
  ok('Pagados muestra las notas pagadas completas: "S/ 110.00 · Pagó todo el … · Efectivo, Yape"')

  await buscador.fill(`andes pagos e2e${s}`)
  await p.waitForFunction((n) => ![...document.querySelectorAll('a')].some((a) => a.innerText.includes(n)), `Botica Rosa Pagos E2E${s}`)
  await tarjeta(`Clinica Andes Pagos E2E${s}`).click()
  await p.waitForURL(`**/pedidos/${andes.id}?desde=cobranzas`)
  await p.goBack()
  await tarjeta(`Clinica Andes Pagos E2E${s}`).waitFor()
  igual(await buscador.inputValue(), `andes pagos e2e${s}`, 'búsqueda al volver')
  ok('el buscador filtra por cliente; al abrir la nota y volver, sigue en Pagados con la misma búsqueda')

  // ------------------------------------------------------------------ con "A favor": 5 pestañas, 3 + 2
  const favor = await despachado(`Botica Favor Pagos E2E${s}`, 2) // S/ 22
  await api(`/api/pedidos/${favor.id}/pagos`, 'POST', { monto: 22, medio: 'EFECTIVO' })
  await api(`/api/pedidos/${favor.id}/correcciones`, 'POST', { cambios: [{ itemId: favor.items[0].id, cantidad: 1 }] })
  await p.goto(`${BASE}/cobranzas`)
  await p.getByRole('tab', { name: /A favor/ }).waitFor()
  igual(await pestanas.count(), 5, 'pestañas con A favor')
  igual(await filas(), 2, 'filas con A favor')
  await p.screenshot({ path: path.join(DIR, 'cobranzas-pestanas-a-favor.png') })
  ok('con un saldo a favor aparecen 5 pestañas, también en dos filas (3 + 2)')

  // ------------------------------------------------------------------ PC: una sola fila
  const pc = await navegador.newContext({ viewport: { width: 1366, height: 768 }, locale: 'es-PE', storageState: await contexto.storageState() })
  const q = await pc.newPage()
  await q.goto(`${BASE}/cobranzas`)
  await q.getByRole('tab', { name: /A favor/ }).waitFor()
  const topsPc = await q.getByRole('tab').evaluateAll((ts) => [...new Set(ts.map((t) => Math.round(t.getBoundingClientRect().top)))].length)
  igual(topsPc, 1, 'filas en la PC')
  await q.screenshot({ path: path.join(DIR, 'cobranzas-pestanas-pc.png') })
  ok('PC: las 5 pestañas en una sola fila')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
