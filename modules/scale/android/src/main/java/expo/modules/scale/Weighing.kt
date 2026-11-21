package expo.modules.scale

import kotlin.math.abs

/** A settled weight worth keeping: one per weighing, numbered so the app saves each once. */
internal data class Measurement(
  val id: Long,
  val value: Double,
  val unit: WeightUnit,
  val kilograms: Double,
  val measuredAt: Long
)

/**
 * Turns a scale's stream of readings into measurements. A weighing runs from the first reading
 * until the scale says the weight was removed; its first settled reading of at least
 * [Protocol.MIN_KILOGRAMS] is its measurement. Scales repeat a settled weight, and Huami's
 * report it once more as the weight is removed, so those repeats are the same measurement. A
 * settled weight that moves by [NEW_WEIGHT_KG] or more within one weighing is someone else, so
 * it is a new measurement.
 */
internal class Weighing(private val clock: () -> Long = System::currentTimeMillis) {
  private var nextId = 1L
  private var over = false

  /** What the scale shows now; null once the weighing is over. */
  var live: Reading? = null
    private set

  /** This weighing's measurement, which stays after it is over until the next one starts. */
  var measurement: Measurement? = null
    private set

  /** Takes one reading. Returns true when it changed [live] or [measurement]. */
  fun feed(reading: Reading): Boolean {
    if (reading.removed) {
      if (over) return false

      over = true
      settle(reading)
      live = null

      return true
    }

    if (over) {
      over = false
      measurement = null
    }

    live = reading
    settle(reading)

    return true
  }

  /** The connection ended: whatever the scale showed is gone, the measurement stays. */
  fun end() {
    over = true
    live = null
  }

  private fun settle(reading: Reading) {
    if (!reading.stable || reading.kilograms < Protocol.MIN_KILOGRAMS) return

    val current = measurement
    if (current != null && abs(current.kilograms - reading.kilograms) < NEW_WEIGHT_KG) return

    measurement = Measurement(
      id = nextId++,
      value = reading.value,
      unit = reading.unit,
      kilograms = reading.kilograms,
      measuredAt = clock()
    )
  }

  companion object {
    const val NEW_WEIGHT_KG = 0.5
  }
}
