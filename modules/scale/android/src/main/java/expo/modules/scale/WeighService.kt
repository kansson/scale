package expo.modules.scale

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The background weighing: the paired scale woke ([ScaleWake]) while the app was not in front. It
 * holds the scale and connects to it at once, saves each weight it settles on to Health Connect,
 * and says so in a dialog over whatever is on screen ([WeighedActivity]). It runs in the
 * foreground, under a quiet notification, from the scale waking until it has gone back to sleep,
 * or for [NEVER_CAME_MS] when it never connects. The app coming to the front takes the weighing
 * over: it shows it itself, and no dialog opens.
 */
class WeighService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val main = Handler(Looper.getMainLooper())
  private var watch: Job? = null
  private var running = false
  /** The measurement shown before this weighing; only later ones are its own. */
  private var before = 0L
  private val handled = mutableSetOf<Long>()
  /** Saves under way, which the service waits for before it stops. */
  private var saving = 0
  private var connected = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    Scale.restore(this)
    Health.restore(this)
    val channel = NotificationChannel(CHANNEL, getString(R.string.scale_weighing_channel), NotificationManager.IMPORTANCE_LOW)
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // A service started in the foreground must say so straight away, whatever it was asked to do.
    startInForeground()

    val address = intent?.getStringExtra(EXTRA_ADDRESS)
    // Seen again while weighing, as a scale advertises again before it sleeps: Scale's own search
    // reconnects, after the pause it gives a scale that just let go.
    if (!running) {
      if (address == null || Scale.isHeldBy(Scale.APP)) stop() else begin(address)
    }
    return START_NOT_STICKY
  }

  private fun begin(address: String) {
    running = true
    before = Scale.state.value.measurement?.id ?: 0L
    Scale.acquire(this, HOLDER)
    Scale.connectNow(address)
    main.postDelayed(end, NEVER_CAME_MS)
    main.postDelayed(longest, LONGEST_MS)
    watch = scope.launch { Scale.state.collect(::onState) }
  }

  private fun onState(state: ScaleState) {
    // The app is in front: it shows the weighing and saves it itself.
    if (Scale.isHeldBy(Scale.APP)) return end.run()

    val measurement = state.measurement
    if (measurement != null && measurement.inBackground && measurement.id > before && handled.add(measurement.id)) {
      save(measurement, state.paired?.name)
    }

    if (state.connection == Connection.CONNECTED) {
      connected = true
      main.removeCallbacks(end)
    } else if (connected) {
      // The scale went back to sleep; it may wake once more, for someone else.
      connected = false
      main.removeCallbacks(end)
      main.postDelayed(end, ASLEEP_MS)
    }
  }

  private fun save(measurement: Measurement, scaleName: String?) {
    saving += 1
    scope.launch {
      val outcome = try {
        if (Health.permitted(this@WeighService)) {
          val id = Health.save(this@WeighService, measurement.kilograms, measurement.measuredAt, measurement.value, measurement.unit)
          Scale.recordSaved(measurement.id, id)
          Weighed.Outcome.SAVED to id
        } else {
          Weighed.Outcome.NOT_ALLOWED to null
        }
      } catch (failed: Exception) {
        Weighed.Outcome.FAILED to null
      }
      // Opened meanwhile, the app shows what became of the weight.
      if (!Scale.isHeldBy(Scale.APP)) {
        WeighedActivity.show(
          this@WeighService,
          Weighed(outcome.first, measurement.value, measurement.unit, measurement.measuredAt, outcome.second, scaleName)
        )
      }
      saving -= 1
      if (!running) stop()
    }
  }

  /** The weighing is over; the service stops once its saves are done. */
  private val end = Runnable {
    if (!running) return@Runnable
    running = false
    main.removeCallbacksAndMessages(null)
    watch?.cancel()
    watch = null
    Scale.release(HOLDER)
    if (saving == 0) stop()
  }

  private val longest = Runnable { end.run() }

  private fun stop() {
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    end.run()
    scope.cancel()
    super.onDestroy()
  }

  private fun startInForeground() {
    val launch = packageManager.getLaunchIntentForPackage(packageName)
    val content = launch?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
    val notification = Notification.Builder(this, CHANNEL)
      .setSmallIcon(R.drawable.scale_weighing)
      .setContentTitle(getString(R.string.scale_weighing_title))
      .setContentText(getString(R.string.scale_weighing_text))
      .setContentIntent(content)
      .setOngoing(true)
      .setCategory(Notification.CATEGORY_SERVICE)
      .build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    } else {
      startForeground(NOTIFICATION, notification)
    }
  }

  companion object {
    private const val CHANNEL = "weighing"
    private const val NOTIFICATION = 1
    private const val HOLDER = "background"
    private const val EXTRA_ADDRESS = "expo.modules.scale.ADDRESS"
    /** How long to wait for the scale to connect after Android saw it wake. */
    private const val NEVER_CAME_MS = 45_000L
    /** How long the scale may sleep before the weighing is over. */
    private const val ASLEEP_MS = 15_000L
    /** The longest the scale is held at a time, should it never let go. */
    private const val LONGEST_MS = 180_000L

    /**
     * Weighs the paired scale at [address], which Android has just seen wake, unless the app is in
     * front and weighs it itself. Starts nothing without Bluetooth's permissions, which Android
     * requires of a connected-device service.
     */
    fun wake(context: Context, address: String) {
      if (Scale.isHeldBy(Scale.APP) || !Scale.hasPermissions(context)) return
      try {
        val intent = Intent(context, WeighService::class.java).putExtra(EXTRA_ADDRESS, address)
        context.startForegroundService(intent)
      } catch (refused: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException: Android refused to start it from here.
      }
    }
  }
}
