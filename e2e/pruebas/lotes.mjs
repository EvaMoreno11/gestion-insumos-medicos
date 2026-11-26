// Editar lotes (corregir y unir), despacho de un solo lote cuando vencen el mismo mes y columna LOTE del PDF.
import fs from 'node:fs'
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

  // ---------------------------------------------------------------- despacho: mismo mes de vencimiento
  const categoriaId = (await api('/api/categorias'))[0].id
  const producto = await api('/api/productos', 'POST', {
    nombre: `GUANTE QUIRÚRGICO ESTÉRIL E2E${s} N° 7.5`, marca: 'ALKHOFAR', categoriaId, presentacion: 'Par', unidadMedida: 'Par', costo: 1.2,
  })
  const entrada = (numeroLote, fechaVencimiento, cantidad) =>
    api('/api/movimientos/entrada', 'POST', { productoId: producto.id, numeroLote, fechaVencimiento, cantidad, costo: 1.2 })
  await entrada('SUELTAS', '2031-03-01', 3) // ya estaban en el almacén
  await entrada('20260318QX', '2031-03-01', 100) // la caja que se compró, mismo mes y año
  await entrada('R2081006', '2032-08-01', 5) // vence después
  const cliente = await api('/api/clientes', 'POST', { nombre: `Botica Lotes E2E ${s}`, porcentaje: 15, locales: [{ nombre: 'Principal', direccion: 'Av. Prueba 1' }] })
  const pedido = await api('/api/pedidos', 'POST', { clienteId: cliente.id })
  await api(`/api/pedidos/${pedido.id}/items`, 'POST', { productoId: producto.id, cantidad: 100 })

  await p.goto(`${BASE}/pedidos/${pedido.id}`)
  p.once('dialog', (d) => d.accept()) // "¿Despachar el pedido?"
  await p.getByRole('button', { name: 'Despachar', exact: true }).click()
  await p.getByText(/lote 20260318QX/).first().waitFor()
  const despachado = await api(`/api/pedidos/${pedido.id}`)
  const lotesSalieron = despachado.items[0].lotes.map((l) => `${l.numeroLote} × ${l.cantidad}`)
  if (lotesSalieron.join() !== '20260318QX × 100') throw new Error('debería salir la caja entera: ' + lotesSalieron)
  await p.screenshot({ path: path.join(DIR, 'despacho-caja-entera.png'), fullPage: true })
  ok(`pedido de 100 con 3 sueltas + caja de 100 (mismo mes): sale ${lotesSalieron.join(', ')}, las sueltas se quedan`)

  // ---------------------------------------------------------------- PDF: el lote en una sola línea
  const pdf = await p.evaluate(async (id) => {
    const r = await fetch(`/api/pedidos/${id}/nota.pdf`)
    return Array.from(new Uint8Array(await r.arrayBuffer()))
  }, pedido.id)
  fs.writeFileSync(path.join(DIR, 'nota-lotes.pdf'), Buffer.from(pdf))
  ok(`nota PDF descargada (${pdf.length} bytes) para revisar la columna LOTE`)

  // ---------------------------------------------------------------- editar un lote
  await p.goto(`${BASE}/inventario/${producto.id}`)
  await p.getByRole('heading', { name: 'Lotes' }).waitFor()
  await p.screenshot({ path: path.join(DIR, 'producto-lotes.png'), fullPage: true })
  await p.getByRole('link', { name: /SUELTAS/ }).click()
  await p.getByRole('heading', { name: 'Editar lote' }).waitFor()
  if ((await p.getByLabel('N° de lote').inputValue()) !== 'SUELTAS') throw new Error('debería venir lleno el número')
  if ((await p.getByLabel('Vencimiento').inputValue()) !== '03/2031') throw new Error('debería venir lleno el vencimiento')
  if (!(await p.getByRole('button', { name: 'Guardar' }).isDisabled())) throw new Error('sin cambios no se guarda')
  await p.getByLabel('N° de lote').fill('s2031-a')
  await p.getByLabel(/Cantidad/).fill('2')
  await p.getByText('Antes 3: queda como ajuste de −1').waitFor()
  await p.getByLabel('Motivo (opcional)').fill('Conteo: había 2')
  await p.screenshot({ path: path.join(DIR, 'editar-lote.png'), fullPage: true })
  await p.getByRole('button', { name: 'Guardar' }).click()
  await p.waitForURL(new RegExp(`/inventario/${producto.id}$`))
  await p.getByRole('link', { name: /S2031-A/ }).waitFor()
  const fila = await p.getByRole('link', { name: /S2031-A/ }).innerText()
  if (!/\b2\b/.test(fila)) throw new Error('debería mostrar 2: ' + fila)
  const historial = await api(`/api/movimientos?productoId=${producto.id}`)
  const ajuste = historial.contenido[0]
  if (ajuste.tipo !== 'AJUSTE' || ajuste.cantidad !== -1 || ajuste.nota !== 'Conteo: había 2') throw new Error(JSON.stringify(ajuste))
  ok('Editar lote: número en mayúsculas (S2031-A), cantidad 3 → 2, y el ajuste −1 queda con su motivo')

  // ---------------------------------------------------------------- unir dos lotes que eran el mismo
  await entrada('S2O31-A', '2031-03-01', 4) // el mismo lote con una O en vez de 0
  await p.reload()
  await p.getByRole('link', { name: /S2O31-A/ }).click()
  await p.getByLabel('N° de lote').fill('S2031-A')
  await p.getByText(/Ya hay otro lote/).waitFor()
  const aviso = await p.getByText(/Al guardar se unen/).innerText()
  if (!aviso.includes('2 + 4 = 6')) throw new Error('el aviso debería sumar 2 + 4 = 6: ' + aviso)
  await p.screenshot({ path: path.join(DIR, 'unir-lotes.png'), fullPage: true })
  await p.getByRole('button', { name: 'Unir y guardar' }).click()
  await p.waitForURL(new RegExp(`/inventario/${producto.id}$`))
  await p.getByRole('link', { name: /S2031-A/ }).waitFor()
  if (await p.getByRole('link', { name: /S2O31-A/ }).count()) throw new Error('el lote mal escrito debería desaparecer')
  const detalle = await api(`/api/productos/${producto.id}`)
  const unido = detalle.lotes.find((l) => l.numeroLote === 'S2031-A')
  if (unido.cantidad !== 6) throw new Error('deberían quedar 6: ' + JSON.stringify(detalle.lotes))
  await p.screenshot({ path: path.join(DIR, 'lotes-unidos.png'), fullPage: true })
  ok(`unir: aviso "2 + 4 = 6" → "Unir y guardar" → queda un solo S2031-A con 6 (lotes: ${detalle.lotes.map((l) => `${l.numeroLote} ${l.cantidad}`).join(', ')})`)

  // ---------------------------------------------------------------- corregir solo el vencimiento
  await p.goto(`${BASE}/inventario/${producto.id}`)
  await p.getByRole('link', { name: /R2081006/ }).click()
  await p.getByLabel('Vencimiento').fill('09/2032')
  await p.getByRole('button', { name: 'Guardar' }).click()
  await p.waitForURL(new RegExp(`/inventario/${producto.id}$`))
  await p.getByText('Vence 09/2032').waitFor()
  ok('corregir solo el vencimiento (08/2032 → 09/2032) no deja ajuste y se ve al volver')

  // ---------------------------------------------------------------- volver sin guardar
  await p.getByRole('link', { name: /R2081006/ }).click()
  await p.getByRole('button', { name: 'Cancelar' }).click()
  await p.waitForURL(new RegExp(`/inventario/${producto.id}$`))
  ok('Cancelar vuelve al producto sin cambiar nada')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
