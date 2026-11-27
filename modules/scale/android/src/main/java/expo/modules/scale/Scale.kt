package expo.modules.scale

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class Connection(val id: String) {
  STOPPED("stopped"),
  SEARCHING("searching"),
  CONNECTING("connecting"),
  CONNECTED("connected")
}

/** Why the app cannot look for the scale. */
internal enum class Problem(val id: String) {
  PERMISSION("permission"),
  BLUETOOTH_OFF("bluetoothOff")
}

/** The scale the app connects to: the first one it found, until it is forgotten. */
internal data class PairedScale(val address: String, val name: String)

internal data class ScaleState(
  val connection: Connection = Connection.STOPPED,
  val problem: Problem? = null,
  val paired: PairedScale? = null,
  val live: Reading? = null,
  val measurement: Measurement? = null
) {
  fun toMap(): Map<String, Any?> = mapOf(
    "connection" to connection.id,
    "problem" to problem?.id,
    "paired" to paired?.let { mapOf("address" to it.address, "name" to it.name) },
    "live" to live?.let {
      mapOf("value" to it.value, "unit" to it.unit.id, "kilograms" to it.kilograms, "stable" to it.stable)
    },
    "measurement" to measurement?.let {
      mapOf(
        "id" to it.id.toDouble(),
        "value" to it.value,
        "unit" to it.unit.id,
        "kilograms" to it.kilograms,
        "measuredAt" to it.measuredAt.toDouble(),
        "recordId" to it.recordId
      )
    }
  )
}

/**
 * The single owner of the scale connection. While someone holds it, it looks for the paired scale
 * (or, before one is paired, any scale), connects when the scale wakes, and turns its readings
 * into measurements. Scales sleep between weighings and drop the connection, so it goes back to
 * looking each time. The app holds it while in front ([APP]); the background weighing holds it
 * while a scale Android saw wake is weighed ([WeighService]), and then looks for the paired scale
 * alone, as Android stops scans without a filter while the screen is off. Everything runs on the
 * main thread.
 */
@SuppressLint("MissingPermission") // Every Bluetooth call follows hasPermissions().
internal object Scale {
  private val main = Handler(Looper.getMainLooper())
  private val _state = MutableStateFlow(ScaleState())
  val state: StateFlow<ScaleState> = _state.asStateFlow()

  /** The app holds the scale while it is in front. */
  const val APP = "app"

  private var context: Context? = null
  /** Who wants the scale read; it is let go of once nobody does. */
  private val holders = mutableSetOf<String>()
  private val started: Boolean
    get() = holders.isNotEmpty()
  /** Let go of while Android looks for the scale itself, to link it ([ScaleWake]). */
  private var heldOff = false
  private var scanning = false
  private var link: ScaleLink? = null
  private var linkName: String? = null
  private val weighing = Weighing()

  val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

  fun hasPermissions(context: Context) = bluetoothPermissions.all {
    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
  }

  /** Reads the paired scale once, so the app shows it before anything connects. */
  fun restore(context: Context) {
    if (this.context != null) return
    this.context = context.applicationContext
    update { it.copy(paired = readPaired(context)) }
  }

  /** The app starts looking for the scale; again after a permission or Bluetooth came back. */
  fun start(context: Context) = acquire(context, APP)

  /** The app lets go of the scale, such as when it leaves the screen. */
  fun stop() = release(APP)

  fun isHeldBy(holder: String) = holder in holders

  /** Starts looking for the scale for [holder], until it is released. */
  fun acquire(context: Context, holder: String) {
    restore(context)
    if (!started) {
      val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
      // A system broadcast: nothing else needs to reach this receiver.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.applicationContext.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        context.applicationContext.registerReceiver(bluetoothReceiver, filter)
      }
    }
    val inFront = APP in holders
    holders += holder
    searchFor(inFront)
  }

  /** [holder] is done with the scale; once nobody holds it, it stops looking and lets go. */
  fun release(holder: String) {
    val inFront = APP in holders
    if (!holders.remove(holder)) return
    if (started) {
      searchFor(inFront)
      return
    }
    main.removeCallbacks(searchAgain)
    runCatching { context?.unregisterReceiver(bluetoothReceiver) }
    stopScan()
    link?.close()
    link = null
    weighing.end()
    update { it.copy(connection = Connection.STOPPED, problem = null, live = null) }
  }

  /**
   * Looks for the scale for whoever holds it now. A scan made for the app in front or for the
   * background starts again when that changed ([inFront] is what it was), as its filter differs.
   */
  private fun searchFor(inFront: Boolean) {
    if (link != null) return
    if (inFront != (APP in holders)) stopScan()
    search()
  }

  /**
   * Connects to the paired scale at [address] at once, without finding it first: Android has just
   * seen it wake ([ScaleWake]). Only while someone holds the scale and nothing is connected.
   */
  fun connectNow(address: String) {
    val context = context ?: return
    val paired = _state.value.paired ?: return
    if (!started || heldOff || link != null || !paired.address.equals(address, ignoreCase = true)) return
    if (!hasPermissions(context)) return
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
    if (!adapter.isEnabled) return
    main.removeCallbacks(searchAgain)
    connect(adapter.getRemoteDevice(paired.address), paired.name)
  }

  /**
   * Lets go of the scale while Android looks for it, as a connected scale stops advertising, and
   * picks it up again after ([ScaleWake.link]).
   */
  fun holdOff(off: Boolean) {
    heldOff = off
    if (off) {
      main.removeCallbacks(searchAgain)
      stopScan()
      link?.close()
    } else {
      search()
    }
  }

  /** The background weighing saved measurement [id] to Health Connect as [recordId]. */
  fun recordSaved(id: Long, recordId: String) {
    update { state ->
      val measurement = state.measurement
      if (measurement?.id == id) state.copy(measurement = measurement.copy(recordId = recordId)) else state
    }
  }

  /**
   * Forgets the paired scale, so the next scale found is paired instead. A running scan matches
   * any scale from now on; a connection to the old scale ends and searching starts again.
   */
  fun forget(context: Context) {
    prefs(context).edit().clear().apply()
    update { it.copy(paired = null) }
    link?.close()
  }

  private fun search() {
    val context = context ?: return
    if (!started || heldOff || link != null) return

    if (!hasPermissions(context)) {
      update { it.copy(connection = Connection.STOPPED, problem = Problem.PERMISSION) }
      return
    }

    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
    if (scanner == null) {
      update { it.copy(connection = Connection.STOPPED, problem = Problem.BLUETOOTH_OFF) }
      return
    }

    // In front, no scan filter: Android's service filter quietly drops a scale that just
    // disconnected, and some scales name their service only in service data. In the background,
    // the paired scale's address, the one filter that keeps a scan going with the screen off.
    val paired = _state.value.paired
    val filters = if (APP in holders) {
      null
    } else {
      paired?.let { listOf(ScanFilter.Builder().setDeviceAddress(it.address).build()) } ?: return
    }

    update { it.copy(connection = Connection.SEARCHING, problem = null) }
    if (scanning) return

    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    scanning = true
    scanner.startScan(filters, settings, scanCallback)
  }

  private val searchAgain = Runnable { search() }

  private fun searchLater(delayMs: Long) {
    main.removeCallbacks(searchAgain)
    main.postDelayed(searchAgain, delayMs)
  }

  private fun stopScan() {
    if (!scanning) return
    scanning = false
    val adapter = context?.getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter?.isEnabled == true) runCatching { adapter.bluetoothLeScanner?.stopScan(scanCallback) }
  }

  private fun isScale(result: ScanResult): Boolean {
    val paired = _state.value.paired
    if (paired != null) return result.device.address == paired.address

    val record = result.scanRecord ?: return false
    val advertised = record.serviceUuids.orEmpty().map(ParcelUuid::getUuid) +
      record.serviceData.orEmpty().keys.map(ParcelUuid::getUuid)

    return advertised.any { it in Protocol.ADVERTISED }
  }

  private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      main.post {
        if (!scanning || heldOff || link != null || !isScale(result)) return@post
        connect(result.device, result.scanRecord?.deviceName ?: result.device.name)
      }
    }

    override fun onScanFailed(errorCode: Int) {
      // This callback's scan is still running, and still reporting.
      if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) return
      // Usually Android throttling scans started too often; it lifts within half a minute.
      main.post {
        scanning = false
        searchLater(RETRY_MS)
      }
    }
  }

  private fun connect(device: BluetoothDevice, name: String?) {
    val context = context ?: return
    stopScan()
    linkName = name ?: device.address
    update { it.copy(connection = Connection.CONNECTING) }
    val next = ScaleLink(context, device, linkListener(device))
    link = next
    next.start()
  }

  private fun linkListener(device: BluetoothDevice) = object : ScaleLink.Listener {
    override fun onReady() {
      val context = context ?: return
      if (_state.value.paired == null) {
        val paired = PairedScale(device.address, linkName ?: device.address)
        prefs(context).edit()
          .putString(KEY_ADDRESS, paired.address)
          .putString(KEY_NAME, paired.name)
          .apply()
        update { it.copy(paired = paired) }
      }
      update { it.copy(connection = Connection.CONNECTED) }
    }

    override fun onReading(reading: Reading) {
      if (weighing.feed(reading)) {
        update { it.copy(live = weighing.live, measurement = measurement(it.measurement)) }
      }
    }

    override fun onClosed(ready: Boolean) {
      link = null
      weighing.end()
      update { it.copy(live = null, measurement = measurement(it.measurement)) }
      if (!started) return
      // A scale that just went to sleep keeps advertising for a moment; let it go quiet first.
      update { it.copy(connection = Connection.SEARCHING) }
      searchLater(if (ready) SETTLE_MS else RETRY_MS)
    }
  }

  private val bluetoothReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
        BluetoothAdapter.STATE_ON -> main.post { search() }
        BluetoothAdapter.STATE_OFF -> main.post {
          scanning = false
          link?.close()
          update { it.copy(connection = Connection.STOPPED, problem = Problem.BLUETOOTH_OFF) }
        }
      }
    }
  }

  /**
   * The weighing's measurement, keeping what is known of it already. A new one is the background
   * weighing's to save unless the app is in front.
   */
  private fun measurement(shown: Measurement?): Measurement? {
    val next = weighing.measurement ?: return null
    if (shown?.id == next.id) return shown
    return next.copy(inBackground = APP !in holders)
  }

  private fun update(change: (ScaleState) -> ScaleState) = _state.update(change)

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun readPaired(context: Context): PairedScale? {
    val prefs = prefs(context)
    val address = prefs.getString(KEY_ADDRESS, null) ?: return null
    return PairedScale(address, prefs.getString(KEY_NAME, null) ?: address)
  }

  private const val PREFS = "scale"
  private const val KEY_ADDRESS = "pairedAddress"
  private const val KEY_NAME = "pairedName"
  private const val SETTLE_MS = 3_000L
  private const val RETRY_MS = 5_000L
}
