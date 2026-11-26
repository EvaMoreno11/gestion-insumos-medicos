// Registrar entrada: botón ÷ del costo, pestañas Registrar | Entradas registradas, búsqueda por producto y lote,
// fechas, volver del producto y la dirección vieja /entrada/ultimas.
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
  const gasa = await api('/api/productos', 'POST', {
    nombre: `ESPARADRAPO E2E${s}`, marca: 'MARCA E2E', categoriaId, presentacion: 'Rollo', unidadMedida: 'Unidad', costo: 2.4,
  })
  const guante = await api('/api/productos', 'POST', {
    nombre: `GUANTE E2E${s}`, marca: 'MARCA E2E', categoriaId, presentacion: 'Caja x 100', unidadMedida: 'Caja', costo: 18,
  })
  await api('/api/movimientos/entrada', 'POST', {
    productoId: guante.id, numeroLote: `ZB-${s}`, fechaVencimiento: '2029-01-31', cantidad: 20, costo: 18.5,
  })

  // ------------------------------------------------------------------ pestaña Registrar y botón ÷
  await p.goto(BASE + '/entrada')
  igual(await p.getByRole('tab', { name: 'Registrar' }).getAttribute('aria-selected'), 'true', 'pestaña inicial')
  igual(await p.getByRole('link', { name: /Últimas entradas/ }).count(), 0, 'botón viejo')
  await p.screenshot({ path: path.join(DIR, 'entradas-registrar-vacio.png') })

  const elegir = async (producto) => {
    await p.getByPlaceholder('¿Qué producto llegó?').fill(producto.nombre)
    await p.getByRole('button', { name: new RegExp(producto.nombre) }).click()
  }
  const costo = p.getByLabel('Costo por unidad (S/)')
  const dividir = p.getByRole('button', { name: /Dividir/ })
  const guardar = p.getByRole('button', { name: 'Guardar y siguiente' })

  await elegir(gasa)
  await p.getByLabel('N° de lote').fill(`ZA${s}`)
  await p.getByLabel('Vencimiento').fill('012030')
  await p.getByLabel('Cantidad (unidad)').fill('12')
  igual(await costo.inputValue(), '2.4', 'el costo viene con el último')
  await costo.tap()
  await costo.fill('')
  await p.keyboard.type('30')
  await dividir.tap()
  const idCampo = await costo.getAttribute('id')
  igual(await p.evaluate(() => document.activeElement?.id), idCampo, 'el foco sigue en el costo después de tocar ÷')
  await p.keyboard.type('12')
  igual(await costo.inputValue(), '30÷12', 'lo escrito')
  await p.getByText('= S/ 2.50 por unidad').waitFor()
  await p.screenshot({ path: path.join(DIR, 'entradas-costo-division.png') })
  ok('30 → ÷ → 12: el teclado no se cierra (el foco sigue en el campo) y debajo dice "= S/ 2.50 por unidad"')

  await costo.fill('30÷')
  await p.getByText('Escribe entre cuántas unidades dividir, p. ej. 30÷12').waitFor()
  igual(await guardar.isDisabled(), true, 'guardar con la división a medias')
  await costo.fill('30÷0')
  await p.getByText('No se puede dividir entre 0').waitFor()
  igual(await guardar.isDisabled(), true, 'guardar dividiendo entre 0')
  await costo.fill('30/12')
  igual(await costo.inputValue(), '30÷12', '"/" se muestra como ÷')
  await dividir.tap()
  igual(await costo.inputValue(), '30÷12', 'un segundo ÷ no se agrega')
  ok('"30÷" pide el número que falta, "30÷0" avisa y no deja guardar, "/" se vuelve ÷ y no se ponen dos ÷')

  await guardar.click()
  await p.getByRole('heading', { name: 'Ingresado ahora (1)' }).waitFor()
  // Una docena a S/ 25: 25 ÷ 12 = 2.0833
  await elegir(gasa)
  await p.getByLabel('Vencimiento').fill('012030')
  await p.getByLabel('Cantidad (unidad)').fill('12')
  await costo.fill('25÷12')
  await p.getByText('= S/ 2.0833 por unidad').waitFor()
  await guardar.click()
  const titulo = p.getByRole('heading', { name: /Ingresado ahora/ })
  await p.getByRole('heading', { name: 'Ingresado ahora (2)' }).waitFor()
  igual((await titulo.innerText()).includes('S/'), false, '"Ingresado ahora" sin monto')
  const ingresado = await p.locator('section').filter({ has: titulo }).innerText()
  if (!ingresado.includes('S/ 2.08 c/u') || !ingresado.includes('S/ 2.50 c/u')) throw new Error('ingresado: ' + ingresado)
  const guardadas = (await api(`/api/movimientos/entradas?buscar=ESPARADRAPO%20E2E${s}`)).lista.contenido
  igual(guardadas.map((e) => e.costoUnitario).join(' '), '2.0833 2.5', 'costos guardados')
  ok('se guardan 2.50 y 2.0833 (4 decimales); "Ingresado ahora (2)" queda como antes, sin monto')

  // ------------------------------------------------------------------ pestaña Entradas registradas
  await p.getByRole('tab', { name: 'Entradas registradas' }).click()
  if (!p.url().includes('pestana=registradas')) throw new Error('url: ' + p.url())
  const estado = p.getByRole('status')
  await estado.filter({ hasText: 'Últimos 7 días' }).waitFor()
  const datos = await api('/api/movimientos/entradas?desde=' + (await p.evaluate(() => {
    const d = new Date(); d.setDate(d.getDate() - 6)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  })) + '&hasta=' + (await p.evaluate(() => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  })))
  const textoEstado = await estado.filter({ hasText: 'Últimos 7 días' }).innerText()
  const dinero = (n) => 'S/ ' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  if (!textoEstado.includes(`${datos.entradas} entradas · ${dinero(datos.total)}`)) throw new Error('estado: ' + textoEstado)
  const hoy = p.getByRole('heading', { name: /^Hoy/i }).first()
  const textoHoy = await hoy.innerText()
  if (!textoHoy.includes(`${datos.dias[0].entradas} entradas · ${dinero(datos.dias[0].total)}`)) throw new Error('hoy: ' + textoHoy)
  await p.screenshot({ path: path.join(DIR, 'entradas-registradas.png') })
  ok(`sin filtros: "${textoEstado}" y el día "${textoHoy}", como los despachados`)

  const buscador = p.getByPlaceholder('Buscar producto o N° de lote…')
  await buscador.fill(`esparadrapo e2e${s}`)
  await estado.filter({ hasText: 'Todo el historial' }).waitFor()
  await estado.filter({ hasText: '2 entradas · S/ 55.00' }).waitFor()
  const tarjetas = p.getByRole('link').filter({ hasText: `ESPARADRAPO E2E${s}` })
  igual(await tarjetas.count(), 2, 'entradas del producto')
  const primera = await tarjetas.first().innerText()
  if (!primera.includes('S/ 2.08 c/u · total S/ 25.00')) throw new Error('tarjeta: ' + primera)
  await p.screenshot({ path: path.join(DIR, 'entradas-buscar-producto.png') })
  ok('buscar el producto: "Todo el historial · 2 entradas · S/ 55.00" y la de 25÷12 dice "S/ 2.08 c/u · total S/ 25.00"')

  await buscador.fill(`zb${s}`)
  await estado.filter({ hasText: '1 entrada · S/ 370.00' }).waitFor()
  await p.getByText(`Lote ZB-${s}`).waitFor()
  ok(`buscar por lote sin el guion ("zb${s}") encuentra el lote ZB-${s}`)

  const hoyIso = await p.evaluate(() => {
    const d = new Date()
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  })
  await buscador.fill('')
  await p.getByRole('button', { name: '📅 Elegir fechas' }).click()
  await p.getByLabel('Desde').fill(hoyIso)
  await p.getByRole('button', { name: 'Ver esas fechas' }).click()
  await estado.filter({ hasText: /^El \d\d\/\d\d\/\d{4}/ }).waitFor()
  if (!p.url().includes(`desde=${hoyIso}`)) throw new Error('url: ' + p.url())
  await p.getByRole('button', { name: 'Quitar fechas' }).click()
  await estado.filter({ hasText: 'Últimos 7 días' }).waitFor()
  ok('"📅 Elegir fechas" filtra solo hoy y la × las quita')

  await buscador.fill(`guante e2e${s}`)
  await estado.filter({ hasText: '1 entrada' }).waitFor()
  await p.getByRole('link').filter({ hasText: `GUANTE E2E${s}` }).click()
  await p.waitForURL(`**/inventario/${guante.id}`)
  await p.goBack()
  await estado.filter({ hasText: '1 entrada' }).waitFor()
  igual(await buscador.inputValue(), `guante e2e${s}`, 'la búsqueda al volver')
  igual(await p.getByRole('tab', { name: 'Entradas registradas' }).getAttribute('aria-selected'), 'true', 'pestaña al volver')
  ok('tocar una entrada abre el producto, y al volver sigue la misma pestaña y búsqueda')

  await p.getByRole('tab', { name: 'Registrar' }).click()
  await p.getByPlaceholder('¿Qué producto llegó?').waitFor()
  igual(await p.getByRole('heading', { name: /Ingresado ahora/ }).count(), 0, 'Ingresado ahora al volver a Registrar')
  ok('al volver a Registrar, "Ingresado ahora" se borró (como al salir de la pantalla)')

  await p.goto(BASE + '/entrada/ultimas')
  await p.waitForURL('**/entrada?pestana=registradas')
  ok('la dirección vieja /entrada/ultimas abre la pestaña Entradas registradas')

  // ------------------------------------------------------------------ PC
  const pc = await navegador.newContext({ viewport: { width: 1366, height: 768 }, locale: 'es-PE', timezoneId: 'America/Lima', storageState: await contexto.storageState() })
  const q = await pc.newPage()
  q.on('pageerror', (e) => errores.push(String(e)))
  await q.goto(`${BASE}/entrada?pestana=registradas`)
  await q.getByRole('status').filter({ hasText: 'Últimos 7 días' }).waitFor()
  await q.screenshot({ path: path.join(DIR, 'entradas-registradas-pc.png') })
  await q.goto(`${BASE}/entrada?producto=${gasa.id}`)
  const costoPc = q.getByLabel('Costo por unidad (S/)')
  await costoPc.click()
  await costoPc.fill('')
  await q.keyboard.type('36/12')
  igual(await costoPc.inputValue(), '36÷12', 'PC con "/"')
  await q.getByText('= S/ 3.00 por unidad').waitFor()
  await q.screenshot({ path: path.join(DIR, 'entradas-costo-pc.png') })
  ok('en la PC, escribir "36/12" con el teclado muestra 36÷12 = S/ 3.00')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
