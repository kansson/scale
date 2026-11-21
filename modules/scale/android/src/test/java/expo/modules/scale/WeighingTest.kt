package expo.modules.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeighingTest {
  private var now = 1_000L
  private val weighing = Weighing { now }

  private fun kg(value: Double, stable: Boolean = false, removed: Boolean = false) =
    Reading(value, WeightUnit.KILOGRAM, stable, removed)

  @Test
  fun `keeps the first settled weight of a weighing`() {
    weighing.feed(kg(40.0))
    weighing.feed(kg(72.3))
    weighing.feed(kg(72.4, stable = true))
    now = 2_000L
    weighing.feed(kg(72.45, stable = true))

    val measurement = weighing.measurement!!
    assertEquals(1L, measurement.id)
    assertEquals(72.4, measurement.kilograms, 0.0001)
    assertEquals(1_000L, measurement.measuredAt)
  }

  @Test
  fun `counts the settled weight sent as it is removed once`() {
    weighing.feed(kg(72.4, stable = true))
    weighing.feed(kg(72.4, stable = true, removed = true))
    assertFalse(weighing.feed(kg(72.4, stable = true, removed = true)))

    assertEquals(1L, weighing.measurement!!.id)
    assertNull(weighing.live)
  }

  @Test
  fun `takes a weighing whose settled weight comes only as it is removed`() {
    weighing.feed(kg(72.0))
    weighing.feed(kg(72.4, stable = true, removed = true))

    assertEquals(72.4, weighing.measurement!!.kilograms, 0.0001)
  }

  @Test
  fun `keeps the measurement after the weighing until the next one starts`() {
    weighing.feed(kg(72.4, stable = true))
    weighing.feed(kg(0.0, removed = true))
    assertNotNull(weighing.measurement)

    weighing.feed(kg(20.0))
    assertNull(weighing.measurement)

    weighing.feed(kg(80.1, stable = true))
    assertEquals(2L, weighing.measurement!!.id)
  }

  @Test
  fun `takes someone else stepping on within one weighing as a new measurement`() {
    weighing.feed(kg(72.4, stable = true))
    weighing.feed(kg(61.0, stable = true))

    assertEquals(2L, weighing.measurement!!.id)
    assertEquals(61.0, weighing.measurement!!.kilograms, 0.0001)
  }

  @Test
  fun `ignores light settled weights`() {
    weighing.feed(kg(4.2, stable = true))

    assertNull(weighing.measurement)
  }

  @Test
  fun `weighs the lightest weight in kilograms whatever the unit`() {
    // 20 lb is 9.07 kg: too light. 160 lb is kept in pounds, as the scale shows it.
    weighing.feed(Reading(20.0, WeightUnit.POUND, stable = true, removed = false))
    assertNull(weighing.measurement)

    weighing.feed(Reading(160.0, WeightUnit.POUND, stable = true, removed = false))
    val measurement = weighing.measurement!!
    assertEquals(WeightUnit.POUND, measurement.unit)
    assertEquals(160.0, measurement.value, 0.0001)
    assertEquals(72.575, measurement.kilograms, 0.001)
  }

  @Test
  fun `measures nothing when the weight is removed before it settles`() {
    weighing.feed(kg(72.0))
    weighing.feed(kg(0.0, removed = true))

    assertNull(weighing.measurement)
    assertNull(weighing.live)
  }

  @Test
  fun `starts afresh after the connection ends`() {
    weighing.feed(kg(72.4, stable = true))
    weighing.end()
    assertNull(weighing.live)
    assertNotNull(weighing.measurement)

    weighing.feed(kg(72.4, stable = true))
    assertEquals(2L, weighing.measurement!!.id)
  }
}
