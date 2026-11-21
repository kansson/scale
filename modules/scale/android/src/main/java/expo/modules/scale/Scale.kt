package expo.modules.scale

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
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
        "measuredAt" to it.measuredAt.toDouble()
      )
    }
  )
}

/**
 * The single owner of the scale connection. While the app is in front it looks for the paired
 * scale (or, before one is paired, any scale), connects when the scale wakes, and turns its
 * readings into measurements. Scales sleep between weighings and drop the connection, so it goes
 * back to looking each time. Everything runs on the main thread.
 */
@SuppressLint("MissingPermission") // Every Bluetooth call follows hasPermissions().
internal object Scale {
  private val main = Handler(Looper.getMainLooper())
  private val _state = MutableStateFlow(ScaleState())
  val state: StateFlow<ScaleState> = _state.asStateFlow()

  private var context: Context? = null
  private var started = false
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

  /** Starts looking for the scale; again after a permission or Bluetooth came back. */
  fun start(context: Context) {
    restore(context)
    if (!started) {
      started = true
      val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
      // A system broadcast: nothing else needs to reach this receiver.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.applicationContext.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        context.applicationContext.registerReceiver(bluetoothReceiver, filter)
      }
    }
    if (link == null) search()
  }

  /** Stops looking and lets go of the scale, such as when the app leaves the screen. */
  fun stop() {
    if (!started) return
    started = false
    main.removeCallbacks(searchAgain)
    runCatching { context?.unregisterReceiver(bluetoothReceiver) }
    stopScan()
    link?.close()
    link = null
    weighing.end()
    update { it.copy(connection = Connection.STOPPED, problem = null, live = null) }
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
    if (!started || link != null) return

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

    update { it.copy(connection = Connection.SEARCHING, problem = null) }
    if (scanning) return

    // No scan filter: Android's service filter quietly drops a scale that just disconnected, and
    // some scales name their service only in service data. The app is in front while it scans.
    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    scanning = true
    scanner.startScan(null, settings, scanCallback)
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
        if (!scanning || link != null || !isScale(result)) return@post
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
        update { it.copy(live = weighing.live, measurement = weighing.measurement) }
      }
    }

    override fun onClosed(ready: Boolean) {
      link = null
      weighing.end()
      update { it.copy(live = null, measurement = weighing.measurement) }
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
