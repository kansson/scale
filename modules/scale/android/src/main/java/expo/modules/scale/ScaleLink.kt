package expo.modules.scale

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * One GATT connection to a scale: connects, finds the profile it speaks, subscribes to its
 * measurement characteristic and sends the start command. Every callback is delivered on the main
 * thread. Scales drop the connection a little after the weighing; [Scale] then searches again.
 */
@SuppressLint("MissingPermission") // Scale checks the Bluetooth permissions before connecting.
internal class ScaleLink(
  private val context: Context,
  private val device: BluetoothDevice,
  private val listener: Listener
) {
  interface Listener {
    /** Subscribed: readings follow as the scale sends them. */
    fun onReady()

    fun onReading(reading: Reading)

    /** The link is finished, and closed; [ready] says whether it got as far as subscribing. */
    fun onClosed(ready: Boolean)
  }

  private val main = Handler(Looper.getMainLooper())
  private var gatt: BluetoothGatt? = null
  private var profile: Profile? = null
  private var ready = false
  private var closed = false
  private var attempts = 0

  private val connectTimeout = Runnable { finish() }

  fun start() {
    attempts += 1
    main.removeCallbacks(connectTimeout)
    main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
  }

  fun close() = finish()

  private fun finish() {
    if (closed) return
    closed = true
    main.removeCallbacks(connectTimeout)
    gatt?.disconnect()
    gatt?.close()
    gatt = null
    listener.onClosed(ready)
  }

  private fun subscribe(gatt: BluetoothGatt) {
    val profile = Protocol.profileFor(gatt.services.map { it.uuid })
    val measurement = profile?.let { gatt.getService(it.service)?.getCharacteristic(it.measurement) }
    val descriptor = measurement?.getDescriptor(Protocol.CLIENT_CONFIG)

    if (profile == null || measurement == null || descriptor == null) {
      finish()
      return
    }

    this.profile = profile
    gatt.setCharacteristicNotification(measurement, true)

    // Body Composition is indicated by the standard; take whichever the scale offers.
    val notifies = measurement.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    val value = if (notifies) {
      BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    } else {
      BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
    }

    val writing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
      @Suppress("DEPRECATION")
      descriptor.value = value
      @Suppress("DEPRECATION")
      gatt.writeDescriptor(descriptor)
    }
    // No onDescriptorWrite follows a write that never started.
    if (!writing) finish()
  }

  /** Asks the scale to start sending; scales without the control characteristic send anyway. */
  private fun sendStart(gatt: BluetoothGatt, profile: Profile) {
    val control = gatt.getService(profile.service)?.getCharacteristic(Protocol.CONTROL) ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      gatt.writeCharacteristic(control, profile.start, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    } else {
      @Suppress("DEPRECATION")
      control.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
      @Suppress("DEPRECATION")
      control.value = profile.start
      @Suppress("DEPRECATION")
      gatt.writeCharacteristic(control)
    }
  }

  private val callback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
      main.post {
        if (closed || gatt != this@ScaleLink.gatt) return@post
        when {
          newState == BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
          // Android often fails a first connection with status 133; one more try usually works.
          !ready && attempts < 2 -> {
            gatt.close()
            this@ScaleLink.gatt = null
            start()
          }
          else -> finish()
        }
      }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
      main.post {
        if (closed || gatt != this@ScaleLink.gatt) return@post
        if (status == BluetoothGatt.GATT_SUCCESS) subscribe(gatt) else finish()
      }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
      main.post {
        val profile = profile
        if (closed || ready || profile == null) return@post
        if (status != BluetoothGatt.GATT_SUCCESS) {
          finish()
          return@post
        }
        ready = true
        main.removeCallbacks(connectTimeout)
        listener.onReady()
        sendStart(gatt, profile)
      }
    }

    override fun onCharacteristicChanged(
      gatt: BluetoothGatt,
      characteristic: BluetoothGattCharacteristic,
      value: ByteArray
    ) {
      val copy = value.copyOf()
      main.post {
        val profile = profile
        if (closed || profile == null || characteristic.uuid != profile.measurement) return@post
        profile.decode(copy)?.let(listener::onReading)
      }
    }

    @Deprecated("Called before Android 13")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
      @Suppress("DEPRECATION")
      onCharacteristicChanged(gatt, characteristic, characteristic.value ?: return)
    }
  }

  private companion object {
    const val CONNECT_TIMEOUT_MS = 15_000L
  }
}
