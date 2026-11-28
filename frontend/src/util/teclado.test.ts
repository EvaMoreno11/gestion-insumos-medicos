import { describe, expect, it } from 'vitest'
import { abreTeclado, deslizoVertical } from './teclado'

describe('cerrar el teclado al deslizar', () => {
  const inicio = { x: 100, y: 400 }

  it('se cierra al deslizar hacia abajo o hacia arriba', () => {
    expect(deslizoVertical(inicio, { x: 100, y: 460 })).toBe(true)
    expect(deslizoVertical(inicio, { x: 100, y: 340 })).toBe(true)
  })

  it('un toque o un movimiento muy corto no lo cierra', () => {
    expect(deslizoVertical(inicio, { x: 100, y: 400 })).toBe(false)
    expect(deslizoVertical(inicio, { x: 103, y: 408 })).toBe(false)
  })

  it('deslizar hacia los lados no lo cierra', () => {
    expect(deslizoVertical(inicio, { x: 180, y: 430 })).toBe(false)
  })

  it('solo cuenta en los campos donde se escribe', () => {
    expect(abreTeclado('INPUT')).toBe(true) // sin tipo = texto
    expect(abreTeclado('input', 'search')).toBe(true)
    expect(abreTeclado('INPUT', 'number')).toBe(true)
    expect(abreTeclado('TEXTAREA')).toBe(true)
    expect(abreTeclado('INPUT', 'checkbox')).toBe(false)
    expect(abreTeclado('SELECT')).toBe(false)
    expect(abreTeclado('BUTTON')).toBe(false)
  })
})
