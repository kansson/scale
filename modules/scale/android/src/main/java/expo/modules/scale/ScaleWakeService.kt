package expo.modules.scale

import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * What Android wakes as the linked scale turns on ([ScaleWake]), with the app closed or not. Being
 * a companion app's service is what allows the background weighing's service to start from the
 * background. Android 12 and later.
 */
@RequiresApi(Build.VERSION_CODES.S)
class ScaleWakeService : CompanionDeviceService() {
  // Android 12 calls this; later versions call the one below, which would otherwise call it too.
  @Deprecated("Android 12's callback")
  override fun onDeviceAppeared(address: String) = ScaleWake.onAppeared(this, address)

  @Deprecated("Android 13 to 15's callback")
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  override fun onDeviceAppeared(associationInfo: AssociationInfo) {
    associationInfo.deviceMacAddress?.let { ScaleWake.onAppeared(this, it.toString()) }
  }

  @RequiresApi(Build.VERSION_CODES.BAKLAVA)
  override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
    // Only the scale turning on; a connection to it is the app's own.
    if (event.event != DevicePresenceEvent.EVENT_BLE_APPEARED) return
    val manager = getSystemService(CompanionDeviceManager::class.java) ?: return
    manager.myAssociations.firstOrNull { it.id == event.associationId }?.deviceMacAddress?.let {
      ScaleWake.onAppeared(this, it.toString())
    }
  }
}
