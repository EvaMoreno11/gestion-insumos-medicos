import { describe, expect, it } from 'vitest'
import { construirUrl, interpretarError } from '../api/http'
import {
  dinero,
  escribirMesAnio,
  leerDecimal,
  leerEntero,
  mesAnio,
  mesAnioAFecha,
  porcentaje,
  unir,
} from './formato'

describe('dinero', () => {
  it('muestra dos decimales y separador de miles', () => {
    expect(dinero(5.5)).toBe('S/ 5.50')
    expect(dinero(1234.5)).toBe('S/ 1,234.50')
    expect(dinero(0)).toBe('S/ 0.00')
  })

  it('sin valor muestra una raya', () => {
    expect(dinero(null)).toBe('—')
    expect(dinero(undefined)).toBe('—')
  })
})

describe('vencimiento por mes y año', () => {
  it('muestra la fecha de la API como mes/año', () => {
    expect(mesAnio('2027-03-31')).toBe('03/2027')
    expect(mesAnio(null)).toBe('')
  })

  it('pone la barra sola mientras se escribe', () => {
    expect(escribirMesAnio('0')).toBe('0')
    expect(escribirMesAnio('03')).toBe('03')
    expect(escribirMesAnio('032')).toBe('03/2')
    expect(escribirMesAnio('032027')).toBe('03/2027')
    expect(escribirMesAnio('03/2027')).toBe('03/2027')
    expect(escribirMesAnio('03-2027abc9')).toBe('03/2027')
  })

  it('convierte lo escrito en la fecha que espera la API', () => {
    expect(mesAnioAFecha('03/2027')).toBe('2027-03-01')
    expect(mesAnioAFecha('3/2027')).toBe('2027-03-01')
    expect(mesAnioAFecha('')).toBeNull() // vacío = no vence
  })

  it('rechaza lo que no es un mes y año válidos', () => {
    expect(mesAnioAFecha('13/2027')).toBeUndefined()
    expect(mesAnioAFecha('00/2027')).toBeUndefined()
    expect(mesAnioAFecha('03/27')).toBeUndefined()
    expect(mesAnioAFecha('marzo')).toBeUndefined()
  })
})

describe('lectura de números', () => {
  it('lee enteros', () => {
    expect(leerEntero(' 12 ')).toBe(12)
    expect(leerEntero('0')).toBe(0)
    expect(leerEntero('1.5')).toBeNull()
    expect(leerEntero('-3')).toBeNull()
    expect(leerEntero('')).toBeNull()
  })

  it('lee decimales con punto o coma', () => {
    expect(leerDecimal('8.50')).toBe(8.5)
    expect(leerDecimal('8,5')).toBe(8.5)
    expect(leerDecimal('10')).toBe(10)
    expect(leerDecimal('abc')).toBeNull()
    expect(leerDecimal('')).toBeNull()
  })
})

describe('textos', () => {
  it('une solo las partes que existen', () => {
    expect(unir(' · ', 'Caja x 100', null, 'Marca A')).toBe('Caja x 100 · Marca A')
    expect(unir(' · ', null, '  ', undefined)).toBe('')
  })

  it('muestra el porcentaje sin ceros de más', () => {
    expect(porcentaje(15)).toBe('15 %')
    expect(porcentaje(12.5)).toBe('12.5 %')
  })
})

describe('cliente HTTP', () => {
  it('arma la URL omitiendo los parámetros vacíos', () => {
    expect(construirUrl('/api/productos', { buscar: 'jer 5ml', categoriaId: null, pagina: 0, vacio: '' })).toBe(
      '/api/productos?buscar=jer+5ml&pagina=0',
    )
    expect(construirUrl('/api/categorias')).toBe('/api/categorias')
  })

  it('usa el detalle del error de la API', () => {
    const error = interpretarError(409, { title: 'Conflicto', detail: 'Ya existe el cliente "Botica Demo"' })
    expect(error.message).toBe('Ya existe el cliente "Botica Demo"')
    expect(error.status).toBe(409)
  })

  it('en errores de validación junta los mensajes de cada campo', () => {
    const error = interpretarError(400, {
      detail: 'Revisa los campos marcados.',
      errores: { nombre: 'El nombre es obligatorio', cantidad: 'Debe ser mayor que 0' },
    })
    expect(error.message).toBe('El nombre es obligatorio. Debe ser mayor que 0')
    expect(error.errores.nombre).toBe('El nombre es obligatorio')
  })

  it('si el servidor no responde con JSON, da un mensaje entendible', () => {
    expect(interpretarError(502, null).message).toBe('El servidor no responde. Intenta de nuevo en un momento.')
    expect(interpretarError(404, null).message).toBe('Error 404')
  })
})
