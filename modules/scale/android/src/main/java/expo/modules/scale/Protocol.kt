package expo.modules.scale

import java.util.UUID

/** The unit a scale shows its weight in. Health Connect always gets kilograms. */
internal enum class WeightUnit(val id: String, private val kilogramsPerUnit: Double) {
  KILOGRAM("kg", 1.0),
  POUND("lb", 0.45359237),
  /** The Chinese jin (斤), half a kilogram. */
  CATTY("catty", 0.5);

  fun toKilograms(value: Double) = value * kilogramsPerUnit
}

/** One weight notification, decoded. `value` is in `unit`, as the scale shows it. */
internal data class Reading(
  val value: Double,
  val unit: WeightUnit,
  /** The scale has settled on this weight. */
  val stable: Boolean,
  /** Nobody is on the scale any more; the weighing is over. */
  val removed: Boolean
) {
  val kilograms: Double
    get() = unit.toKilograms(value)
}

/** Which of the two GATT profiles a scale speaks; docs/PROTOCOL.md describes both. */
internal enum class Profile(val service: UUID, val measurement: UUID, val start: ByteArray) {
  /** Body Composition (Mi Body Composition Scale, Mi Scale 2): `0x181B` / `0x2A9C`. */
  BODY_COMPOSITION(
    Protocol.BODY_COMPOSITION_SERVICE,
    Protocol.BODY_COMPOSITION_MEASUREMENT,
    byteArrayOf(0x06, 0x0b, 0x00, 0x00)
  ),

  /** Weight Scale (Mi Scale and other standard scales): `0x181D` / `0x2A9D`. */
  WEIGHT_SCALE(
    Protocol.WEIGHT_SCALE_SERVICE,
    Protocol.WEIGHT_MEASUREMENT,
    byteArrayOf(0x03, 0x01, 0x00, 0x00, 0x00)
  );

  fun decode(bytes: ByteArray): Reading? = when (this) {
    BODY_COMPOSITION -> Protocol.decodeBodyComposition(bytes)
    WEIGHT_SCALE -> Protocol.decodeWeight(bytes)
  }
}

/**
 * The scales' Bluetooth protocol: the services and characteristics to use, and the two
 * measurement formats. Kept in step with docs/PROTOCOL.md and ProtocolTest.kt.
 */
internal object Protocol {
  val BODY_COMPOSITION_SERVICE: UUID = uuid16(0x181B)
  val WEIGHT_SCALE_SERVICE: UUID = uuid16(0x181D)

  val BODY_COMPOSITION_MEASUREMENT: UUID = uuid16(0x2A9C)
  val WEIGHT_MEASUREMENT: UUID = uuid16(0x2A9D)
  /** Where the start command goes, in either profile's service. */
  val CONTROL: UUID = uuid16(0x2A2B)
  val CLIENT_CONFIG: UUID = uuid16(0x2902)

  /**
   * A scale advertises at least one of these, as a service or as service data. Not Huami's own
   * `0xFEE0`: their scales advertise it beside `0x181B`, and so do their bands and watches, which
   * speak neither profile.
   */
  val ADVERTISED = setOf(BODY_COMPOSITION_SERVICE, WEIGHT_SCALE_SERVICE)

  /** Anything lighter is a bag or a cat, not a weighing worth keeping. */
  const val MIN_KILOGRAMS = 10.0

  /** Body composition first: those scales offer the weight scale service as well. */
  fun profileFor(services: Collection<UUID>): Profile? = when {
    BODY_COMPOSITION_SERVICE in services -> Profile.BODY_COMPOSITION
    WEIGHT_SCALE_SERVICE in services -> Profile.WEIGHT_SCALE
    else -> null
  }

  /**
   * A `0x2A9D` Weight Measurement: flags, then the weight as a little-endian `uint16`, in steps of
   * 5 g, or 0.01 lb or jin. An optional timestamp and user follow, which are not needed: the
   * reading arrives as it is taken, and the scale's clock is often wrong.
   */
  fun decodeWeight(bytes: ByteArray): Reading? {
    if (bytes.size < 3) return null

    val flags = bytes.u8(0)
    val raw = bytes.u16(1)
    // The standard's "measurement unsuccessful".
    if (raw == 0xFFFF) return null

    val unit = when {
      flags.bit(0) -> WeightUnit.POUND
      flags.bit(4) -> WeightUnit.CATTY
      else -> WeightUnit.KILOGRAM
    }
    val removed = flags.bit(7)

    return Reading(
      value = if (unit == WeightUnit.KILOGRAM) raw / 200.0 else raw / 100.0,
      unit = unit,
      stable = flags.bit(5),
      removed = removed
    )
  }

  /**
   * A `0x2A9C` Body Composition Measurement as Huami's scales send it: two flag bytes, a
   * timestamp, the impedance and the weight as a little-endian `uint16` at bytes 11–12.
   */
  fun decodeBodyComposition(bytes: ByteArray): Reading? {
    if (bytes.size < 13) return null

    val units = bytes.u8(0)
    val state = bytes.u8(1)
    val raw = bytes.u16(11)
    if (raw == 0xFFF0 || raw == 0xFFFF) return null

    val unit = when {
      units.bit(0) -> WeightUnit.POUND
      state.bit(6) -> WeightUnit.CATTY
      else -> WeightUnit.KILOGRAM
    }

    return Reading(
      value = if (unit == WeightUnit.KILOGRAM) raw / 200.0 else raw / 100.0,
      unit = unit,
      stable = state.bit(5),
      removed = state.bit(7)
    )
  }

  private fun uuid16(short: Int): UUID =
    UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(short))

  private fun ByteArray.u8(index: Int) = this[index].toInt() and 0xFF

  private fun ByteArray.u16(index: Int) = u8(index) or (u8(index + 1) shl 8)

  private fun Int.bit(index: Int) = (this shr index) and 1 == 1
}
