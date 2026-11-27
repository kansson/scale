package expo.modules.scale

import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What became of asking Android to watch for the paired scale. */
internal enum class LinkResult(val id: String) {
  LINKED("linked"),
  /** Declined, or the scale was not found while Android looked for it. */
  NOT_LINKED("notLinked"),
  /** The scale is being linked already. */
  BUSY("busy"),
  /** The phone cannot (before Android 12, or without companion devices), or no scale is paired. */
  UNAVAILABLE("unavailable")
}

/**
 * Weigh in the background. The paired scale is linked to the app as a companion device
 * ([CompanionDeviceManager]), which Android confirms once in its own dialog. Android then watches
 * for the scale itself, at no cost to the app, and wakes [ScaleWakeService] as it turns on; the
 * background weighing starts then ([WeighService.wake]). Being linked is the setting: turned off,
 * the link goes, and a scale forgotten is unlinked. Android 12 and later. Main thread only.
 */
internal object ScaleWake {
  private val main = Handler(Looper.getMainLooper())
  private val _linked = MutableStateFlow(false)
  /** Android watches for the paired scale. */
  val linked: StateFlow<Boolean> = _linked.asStateFlow()

  private var context: Context? = null
  /** Who waits to hear how linking the scale went. */
  private var linking: ((LinkResult) -> Unit)? = null

  /** Whether the phone can watch for the scale for the app. */
  fun supported(context: Context) =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

  fun restore(context: Context) {
    if (this.context != null) return
    this.context = context.applicationContext
    Scale.restore(context)
    sync()
  }

  /**
   * Asks Android to watch for the paired scale, which it confirms in a dialog over [activity].
   * Android looks for the scale while asking, so the app lets go of it meanwhile: a scale
   * connected stops advertising.
   */
  fun link(activity: Activity, done: (LinkResult) -> Unit) {
    restore(activity)
    val manager = manager() ?: return done(LinkResult.UNAVAILABLE)
    val paired = Scale.state.value.paired ?: return done(LinkResult.UNAVAILABLE)
    if (linking != null) return done(LinkResult.BUSY)
    if (associations(manager).any { it.address == paired.address.uppercase() }) {
      sync()
      return done(LinkResult.LINKED)
    }
    linking = done
    Scale.holdOff(true)
    // Android's dialog may never answer, as when the app reloads under it: give up in the end.
    main.postDelayed(giveUp, LINK_TIMEOUT_MS)
    val filter = BluetoothLeDeviceFilter.Builder()
      .setScanFilter(ScanFilter.Builder().setDeviceAddress(paired.address).build())
      .build()
    val request = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build()
    val callback = object : CompanionDeviceManager.Callback() {
      // Android 12 hands over its dialog here, later versions in onAssociationPending.
      @Deprecated("Android 12's callback")
      override fun onDeviceFound(sender: IntentSender) = confirm(activity, sender)

      override fun onAssociationPending(sender: IntentSender) = confirm(activity, sender)

      // Also after the dialog's own answer finished the linking: Android watches the scale then.
      override fun onAssociationCreated(association: AssociationInfo) {
        if (linking == null) sync() else finish()
      }

      override fun onFailure(error: CharSequence?) = finish()
    }
    runCatching { manager.associate(request, callback, main) }.onFailure { finish() }
  }

  private fun confirm(activity: Activity, sender: IntentSender) {
    runCatching { activity.startIntentSenderForResult(sender, REQUEST_LINK, null, 0, 0, 0) }
      .onFailure { finish() }
  }

  /**
   * Android's dialog closed: [ScaleModule] passes its result on. Confirmed, the link may only be
   * made a moment later ([CompanionDeviceManager.Callback.onAssociationCreated]): it waits for it.
   */
  fun onLinkResult(resultCode: Int) {
    if (resultCode != Activity.RESULT_OK) return finish()
    main.removeCallbacks(giveUp)
    main.postDelayed(giveUp, CONFIRMED_WAIT_MS)
  }

  private val giveUp = Runnable { finish() }

  /** Nothing waits for the linking any more, such as after a reload: the scale is read again. */
  fun cancelLinking() {
    if (linking == null) return
    linking = null
    main.removeCallbacks(giveUp)
    Scale.holdOff(false)
  }

  /** Ends the linking; whether the scale is linked is what Android says. */
  private fun finish() {
    val done = linking ?: return
    linking = null
    main.removeCallbacks(giveUp)
    Scale.holdOff(false)
    sync()
    done(if (_linked.value) LinkResult.LINKED else LinkResult.NOT_LINKED)
  }

  /** Weigh in the background is turned off: Android stops watching for the scale. */
  fun unlink(context: Context) {
    restore(context)
    val manager = manager() ?: return
    associations(manager).forEach { unlink(manager, it) }
    sync()
  }

  /**
   * Has Android watch for the paired scale if it is linked; links to any other scale, such as
   * one forgotten, go.
   */
  fun sync() {
    val manager = manager() ?: return
    val paired = Scale.state.value.paired?.address?.uppercase()
    var linked = false
    associations(manager).forEach { association ->
      if (association.address != paired) {
        unlink(manager, association)
      } else {
        // Asking again is harmless: Android watches for each address once.
        runCatching { observe(manager, association.address) }.onSuccess { linked = true }
      }
    }
    _linked.value = linked
  }

  /** Android saw [address] turn on: weighs it, if it is still the paired scale. */
  fun onAppeared(context: Context, address: String) {
    restore(context)
    val paired = Scale.state.value.paired ?: return
    if (!paired.address.equals(address, ignoreCase = true)) return
    WeighService.wake(context, paired.address)
  }

  private data class Association(val id: Int, val address: String)

  private fun manager(): CompanionDeviceManager? {
    val context = context ?: return null
    if (!supported(context)) return null
    return context.getSystemService(CompanionDeviceManager::class.java)
  }

  @Suppress("DEPRECATION")
  private fun associations(manager: CompanionDeviceManager): List<Association> =
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.myAssociations.mapNotNull { info ->
          info.deviceMacAddress?.toString()?.uppercase()?.let { Association(info.id, it) }
        }
      } else {
        manager.associations.map { Association(-1, it.uppercase()) }
      }
    }.getOrDefault(emptyList())

  @Suppress("DEPRECATION")
  @RequiresApi(Build.VERSION_CODES.S)
  private fun observe(manager: CompanionDeviceManager, address: String) =
    manager.startObservingDevicePresence(address)

  @Suppress("DEPRECATION")
  private fun unlink(manager: CompanionDeviceManager, association: Association) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      runCatching { manager.stopObservingDevicePresence(association.address) }
    }
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.disassociate(association.id)
      } else {
        manager.disassociate(association.address)
      }
    }
  }

  /** The request code of Android's dialog linking the scale. */
  const val REQUEST_LINK = 0x5343
  /** How long Android's dialog may take, looking for the scale and waiting for an answer. */
  private const val LINK_TIMEOUT_MS = 180_000L
  /** How long a confirmed link may take to be made. */
  private const val CONFIRMED_WAIT_MS = 2_000L
}
