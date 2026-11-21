package expo.modules.scale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.UUID
import org.junit.Test

/**
 * Frames built from the layouts in docs/PROTOCOL.md. Add frames captured from a real scale here,
 * with the scale's model, as they are found.
 */
class ProtocolTest {
  private fun frame(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

  @Test
  fun `reads a settled weight in kilograms`() {
    // Flags: timestamp, settled. 0x3890 = 14480 × 5 g. 2026-11-21 08:30:00.
    val reading = Protocol.decodeWeight(frame("22 90 38 ea 07 0b 15 08 1e 00"))!!

    assertEquals(72.4, reading.value, 0.0001)
    assertEquals(WeightUnit.KILOGRAM, reading.unit)
    assertTrue(reading.stable)
    assertFalse(reading.removed)
  }

  @Test
  fun `reads a weight still settling`() {
    val reading = Protocol.decodeWeight(frame("02 90 38 ea 07 0b 15 08 1e 00"))!!

    assertFalse(reading.stable)
  }

  @Test
  fun `reads pounds and jin in hundredths`() {
    val pounds = Protocol.decodeWeight(frame("23 59 3e"))!!
    val jin = Protocol.decodeWeight(frame("32 90 38"))!!

    assertEquals(WeightUnit.POUND, pounds.unit)
    assertEquals(159.61, pounds.value, 0.0001)
    assertEquals(72.398, pounds.kilograms, 0.001)
    assertEquals(WeightUnit.CATTY, jin.unit)
    assertEquals(144.8, jin.value, 0.0001)
    assertEquals(72.4, jin.kilograms, 0.0001)
  }

  @Test
  fun `reads the weight being removed`() {
    val reading = Protocol.decodeWeight(frame("a2 90 38 ea 07 0b 15 08 1e 00"))!!

    assertTrue(reading.removed)
  }

  @Test
  fun `skips unsuccessful and short weight frames`() {
    assertNull(Protocol.decodeWeight(frame("02 ff ff")))
    assertNull(Protocol.decodeWeight(frame("02 90")))
  }

  @Test
  fun `reads a settled body composition weight`() {
    // Flags 02 22: impedance measured, settled. Impedance 500 Ω, then 14480 × 5 g.
    val reading = Protocol.decodeBodyComposition(frame("02 22 ea 07 0b 15 08 1e 00 f4 01 90 38"))!!

    assertEquals(72.4, reading.value, 0.0001)
    assertEquals(WeightUnit.KILOGRAM, reading.unit)
    assertTrue(reading.stable)
    assertFalse(reading.removed)
  }

  @Test
  fun `reads a body composition weight removed after settling`() {
    val reading = Protocol.decodeBodyComposition(frame("02 a2 ea 07 0b 15 08 1e 00 f4 01 90 38"))!!

    assertTrue(reading.stable)
    assertTrue(reading.removed)
  }

  @Test
  fun `reads body composition pounds and jin`() {
    val pounds = Protocol.decodeBodyComposition(frame("03 20 ea 07 0b 15 08 1e 00 00 00 59 3e"))!!
    val jin = Protocol.decodeBodyComposition(frame("02 60 ea 07 0b 15 08 1e 00 00 00 90 38"))!!

    assertEquals(WeightUnit.POUND, pounds.unit)
    assertEquals(159.61, pounds.value, 0.0001)
    assertEquals(WeightUnit.CATTY, jin.unit)
    assertEquals(72.4, jin.kilograms, 0.0001)
  }

  @Test
  fun `skips short and placeholder body composition frames`() {
    assertNull(Protocol.decodeBodyComposition(frame("02 22 ea 07 0b 15 08 1e 00 f4 01 90")))
    assertNull(Protocol.decodeBodyComposition(frame("02 22 ea 07 0b 15 08 1e 00 f4 01 f0 ff")))
    assertNull(Protocol.decodeBodyComposition(frame("02 22 ea 07 0b 15 08 1e 00 f4 01 ff ff")))
  }

  @Test
  fun `reads the weight from a frame with a user ID after the timestamp`() {
    // Flags: timestamp, user ID, settled. User 1 follows the timestamp.
    val reading = Protocol.decodeWeight(frame("26 90 38 ea 07 0b 15 08 1e 00 01"))!!

    assertEquals(72.4, reading.value, 0.0001)
    assertTrue(reading.stable)
  }

  @Test
  fun `prefers body composition when a scale offers both profiles`() {
    val both = listOf(Protocol.WEIGHT_SCALE_SERVICE, Protocol.BODY_COMPOSITION_SERVICE)

    assertEquals(Profile.BODY_COMPOSITION, Protocol.profileFor(both))
    assertEquals(Profile.WEIGHT_SCALE, Protocol.profileFor(listOf(Protocol.WEIGHT_SCALE_SERVICE)))
    // Huami's own 0xFEE0, which their bands advertise too, is neither profile.
    assertNull(Protocol.profileFor(listOf(UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb"))))
  }
}
