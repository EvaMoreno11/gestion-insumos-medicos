// Comprueba que ya no están los textos explicativos y saca capturas del inicio y de las pestañas.
import path from 'node:path'
import { devices } from 'playwright-core'
import { BASE, entrar, CAPTURAS as DIR } from '../util.mjs'

let pasos = 0
const ok = (t) => console.log('  ✓', t, ++pasos && '')
const QUITADOS = [
  '¿Tengo este producto?', 'Registrar y despachar', 'Mercadería que llega', 'Lo que falta y lo agregado a mano',
  'Pagos y créditos', 'Lo que falta pagarles', 'Stock bajo y vencimientos', 'Ventas, ganancia y compras',
  'Agregar y editar', 'Pasó el plazo y aún deben', 'Tienen plazo y todavía no vence', 'nadie anotó todavía',
  'Notas que ya se pagaron completas', 'Primero las más antiguas, para que ninguna se olvide',
  'Primero las que se terminaron de pagar hace menos', 'Solo las compras que no se pagan al contado',
  'Lotes que vencen en el próximo año', 'Lotes vencidos que aún tienen unidades',
]

export default async function prueba(navegador) {
  const contexto = await navegador.newContext({ ...devices['iPhone 14'], locale: 'es-PE' })
  const p = await contexto.newPage()
  p.setDefaultTimeout(20000)
  const errores = []
  p.on('pageerror', (e) => errores.push(String(e)))

  await entrar(p)

  const revisar = async (pagina, url, espera) => {
    await pagina.goto(BASE + url)
    await pagina.getByText(espera).first().waitFor()
    await pagina.waitForTimeout(800)
    const texto = await pagina.locator('body').innerText()
    const quedan = QUITADOS.filter((q) => texto.includes(q))
    if (quedan.length) throw new Error(`${url} todavía dice: ${quedan.join(' | ')}`)
  }

  await revisar(p, '/', 'Consultar inventario')
  await p.screenshot({ path: path.join(DIR, 'inicio-sin-textos.png'), fullPage: true })
  for (const ver of ['vencidos', 'por-cobrar', 'sin-plazo', 'pagados']) {
    await revisar(p, `/cobranzas?ver=${ver}`, 'Te deben')
  }
  await p.screenshot({ path: path.join(DIR, 'cobranzas-sin-textos.png') })
  await revisar(p, '/proveedores', 'Falta pagar')
  await revisar(p, '/proveedores?ver=pagadas', 'Falta pagar')
  for (const ver of ['por-vencer', 'vencidos']) {
    await revisar(p, `/alertas?ver=${ver}`, 'Stock bajo')
  }
  ok('ya no aparece ninguno de los 18 textos quitados (inicio, Cobranzas, Proveedores y Alertas)')

  const pc = await (await navegador.newContext({ viewport: { width: 1366, height: 768 }, locale: 'es-PE', storageState: await contexto.storageState() })).newPage()
  await revisar(pc, '/', 'Consultar inventario')
  await pc.screenshot({ path: path.join(DIR, 'inicio-sin-textos-pc.png') })
  ok('capturas del inicio en el celular y en la PC')

  if (errores.length) throw new Error('errores en la página: ' + errores.join(' | '))
  console.log(`\n${pasos} comprobaciones OK`)
}
