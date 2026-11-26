// Lo que comparten las pruebas de navegador: dónde está la app, qué navegador usar, cómo entrar
// y cómo consultar la base de datos de prueba (para mover fechas, por ejemplo).
import fs from 'node:fs'
import path from 'node:path'
import pg from 'pg'

/** La app: el servidor de desarrollo de Vite (5173) o el backend con la app ya compilada (8080). */
export const BASE = process.env.BASE || 'http://localhost:5173'

/** Navegador instalado en la computadora: msedge en Windows; en GitHub se usa chrome. */
export const CANAL = process.env.E2E_NAVEGADOR || 'msedge'

/** Base de datos de PRUEBA (nunca la de producción): la del docker-compose por defecto. */
const BASE_DE_DATOS = process.env.E2E_DB || 'postgresql://inventario:inventario@localhost:5433/inventario'

/** Carpeta de las capturas que toman las pruebas (no se sube al repositorio). */
export const CAPTURAS = process.env.E2E_CAPTURAS || path.join(import.meta.dirname, 'resultados')
fs.mkdirSync(CAPTURAS, { recursive: true })

/** Cuenta solo para las pruebas: se crea la primera vez y después se entra con ella. */
const USUARIO = 'prueba-e2e'
const CLAVE = 'clave-de-prueba-e2e'

/** Abre la app y entra. Si la base está vacía, crea la cuenta (la de prueba, salvo que se indique otra). */
export async function entrar(p, usuario = USUARIO) {
  await p.goto(BASE + '/')
  const crear = p.getByRole('button', { name: 'Crear cuenta y entrar' })
  const ingresar = p.getByRole('button', { name: 'Entrar', exact: true })
  await crear.or(ingresar).first().waitFor()
  await p.getByLabel('Usuario').fill(usuario)
  await p.getByLabel('Contraseña', { exact: true }).fill(CLAVE)
  if (await crear.isVisible()) {
    await p.getByLabel('Repite la contraseña').fill(CLAVE)
    await crear.click()
  } else {
    await ingresar.click()
  }
  await p.getByLabel('Usuario').waitFor({ state: 'detached' })
}

let conexion
/** Ejecuta una consulta en la base de prueba y devuelve las filas. */
export async function sql(consulta, valores = []) {
  if (!conexion) {
    conexion = new pg.Client({ connectionString: BASE_DE_DATOS })
    await conexion.connect()
  }
  return (await conexion.query(consulta, valores)).rows
}

export async function cerrarSql() {
  await conexion?.end()
}

/** Espera a que la app responda (en GitHub el backend tarda unos segundos en arrancar). */
export async function esperarApp(segundos = 120) {
  const limite = Date.now() + segundos * 1000
  while (Date.now() < limite) {
    try {
      if ((await fetch(BASE + '/api/health')).ok) return
    } catch {
      // todavía no arranca
    }
    await new Promise((r) => setTimeout(r, 2000))
  }
  throw new Error(`la app no respondió en ${BASE} después de ${segundos} s`)
}
