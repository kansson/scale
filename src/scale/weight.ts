import type { WeightUnit } from './state'

/**
 * A weight as the scale shows it: kilograms in tenths, as every common scale displays them, and
 * pounds and jin likewise. The scale measures in 5 g steps, finer than anyone reads a weight.
 */
export function formatWeight(value: number, locale: string) {
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value)
}

/** The unit's symbol; jin keeps its Chinese character, as the scale's own display does. */
export function unitSymbol(unit: WeightUnit) {
  return unit === 'catty' ? '斤' : unit
}
