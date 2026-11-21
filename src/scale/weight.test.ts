import { describe, expect, it } from 'vitest'
import { formatWeight, unitSymbol } from './weight'

describe('formatWeight', () => {
  it('shows tenths in the locale’s style', () => {
    expect(formatWeight(72.4, 'en')).toBe('72.4')
    expect(formatWeight(72.46, 'en')).toBe('72.5')
    expect(formatWeight(72, 'sv')).toBe('72,0')
  })

  it('names jin by its character', () => {
    expect(unitSymbol('catty')).toBe('斤')
    expect(unitSymbol('kg')).toBe('kg')
  })
})
