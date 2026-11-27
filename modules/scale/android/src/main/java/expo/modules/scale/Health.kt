package expo.modules.scale

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.kilograms
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** A weight this app saved to Health Connect, as the scale showed it. */
internal data class SavedWeight(val recordId: String, val value: Double, val unit: WeightUnit, val measuredAt: Long)

internal data class SavedWeights(
  /** The last weight saved, which Settings can undo; kept on the phone. */
  val last: SavedWeight? = null,
  /** Records undone since the app started, so a weight shown can say it was removed. */
  val undone: Set<String> = emptySet()
) {
  fun toMap(): Map<String, Any?> = mapOf(
    "last" to last?.let {
      mapOf("recordId" to it.recordId, "value" to it.value, "unit" to it.unit.id, "measuredAt" to it.measuredAt.toDouble())
    },
    "undone" to undone.toList()
  )
}

/**
 * Writes weights to Health Connect, the phone's store of health data that other apps read, and
 * keeps the last one written, from the app or the background weighing alike, so it can be undone.
 * Safe from any thread.
 */
internal object Health {
  val permissions = setOf(HealthPermission.getWritePermission(WeightRecord::class))

  /** The Play Store app that provides Health Connect before Android 14. */
  private const val PROVIDER = "com.google.android.apps.healthdata"

  /** `available`, `updateRequired` (install or update it from the Play Store) or `unavailable`. */
  fun status(context: Context): String = when (HealthConnectClient.getSdkStatus(context)) {
    HealthConnectClient.SDK_AVAILABLE -> "available"
    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "updateRequired"
    else -> "unavailable"
  }

  suspend fun permitted(context: Context): Boolean {
    if (status(context) != "available") return false
    return client(context).permissionController.getGrantedPermissions().containsAll(permissions)
  }

  /**
   * Saves one weight and returns its record's id. The client record id is the time it was
   * measured, so saving the same measurement again replaces it rather than adding a second.
   */
  suspend fun save(context: Context, kilograms: Double, measuredAt: Long, value: Double, unit: WeightUnit): String {
    val time = Instant.ofEpochMilli(measuredAt)
    val record = WeightRecord(
      time = time,
      zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
      weight = kilograms.kilograms,
      metadata = Metadata.autoRecorded(
        device = Device(type = Device.TYPE_SCALE),
        clientRecordId = "weight-$measuredAt",
        clientRecordVersion = 1
      )
    )

    val id = client(context).insertRecords(listOf(record)).recordIdsList.first()
    remember(context, SavedWeight(id, value, unit, measuredAt))
    return id
  }

  private val _saved = MutableStateFlow(SavedWeights())
  val saved: StateFlow<SavedWeights> = _saved.asStateFlow()
  @Volatile private var restored = false

  /** Reads the last weight saved, once. */
  fun restore(context: Context): Unit = synchronized(this) {
    if (restored) return
    restored = true
    val prefs = prefs(context)
    val id = prefs.getString(KEY_RECORD, null)
    val unit = WeightUnit.entries.firstOrNull { it.id == prefs.getString(KEY_UNIT, null) }
    val value = prefs.getString(KEY_VALUE, null)?.toDoubleOrNull()
    val last = if (id != null && unit != null && value != null) {
      SavedWeight(id, value, unit, prefs.getLong(KEY_MEASURED_AT, 0L))
    } else {
      null
    }
    _saved.update { it.copy(last = last) }
  }

  private fun remember(context: Context, weight: SavedWeight): Unit = synchronized(this) {
    restore(context)
    prefs(context).edit()
      .putString(KEY_RECORD, weight.recordId)
      .putString(KEY_VALUE, weight.value.toString())
      .putString(KEY_UNIT, weight.unit.id)
      .putLong(KEY_MEASURED_AT, weight.measuredAt)
      .apply()
    _saved.update { it.copy(last = weight) }
  }

  /**
   * Removes a weight this app saved, such as one saved for someone else. Throws when Health
   * Connect refuses. A weight saved while Health Connect was deleting stays the last one.
   */
  suspend fun undo(context: Context, recordId: String) {
    restore(context)
    client(context).deleteRecords(WeightRecord::class, listOf(recordId), emptyList())
    synchronized(this) {
      val next = _saved.updateAndGet { saved ->
        saved.copy(last = saved.last?.takeUnless { it.recordId == recordId }, undone = saved.undone + recordId)
      }
      if (next.last == null) prefs(context).edit().clear().apply()
    }
  }

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private const val PREFS = "health"
  private const val KEY_RECORD = "lastRecordId"
  private const val KEY_VALUE = "lastValue"
  private const val KEY_UNIT = "lastUnit"
  private const val KEY_MEASURED_AT = "lastMeasuredAt"

  /** Health Connect's own screen, where the user sees the data and the apps allowed to it. */
  fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

  /** Health Connect's Play Store page, for phones that need to install or update it. */
  fun installIntent(): Intent = Intent(
    Intent.ACTION_VIEW,
    Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
  ).setPackage("com.android.vending")

  private fun client(context: Context) = HealthConnectClient.getOrCreate(context)
}
