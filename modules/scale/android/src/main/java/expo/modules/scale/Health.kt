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

/** Writes weights to Health Connect, the phone's store of health data that other apps read. */
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
  suspend fun save(context: Context, kilograms: Double, measuredAt: Long): String {
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

    return client(context).insertRecords(listOf(record)).recordIdsList.first()
  }

  /** Removes a weight this app saved, such as one saved for someone else. */
  suspend fun delete(context: Context, id: String) {
    client(context).deleteRecords(WeightRecord::class, listOf(id), emptyList())
  }

  /** Health Connect's own screen, where the user sees the data and the apps allowed to it. */
  fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

  /** Health Connect's Play Store page, for phones that need to install or update it. */
  fun installIntent(): Intent = Intent(
    Intent.ACTION_VIEW,
    Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
  ).setPackage("com.android.vending")

  private fun client(context: Context) = HealthConnectClient.getOrCreate(context)
}
