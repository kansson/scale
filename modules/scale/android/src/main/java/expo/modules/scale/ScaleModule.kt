package expo.modules.scale

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.health.connect.client.PermissionController
import expo.modules.kotlin.Promise
import expo.modules.kotlin.activityresult.AppContextActivityResultContract
import expo.modules.kotlin.activityresult.AppContextActivityResultLauncher
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The JS face of [Scale] and [Health]. JS starts and stops looking and saves what it measured. */
class ScaleModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  private var watcher: Job? = null
  private val main = Handler(Looper.getMainLooper())

  override fun definition() = ModuleDefinition {
    Name("Scale")

    OnCreate { appContext.reactContext?.let(Scale::restore) }

    Events("onStateChange")

    OnStartObserving("onStateChange") {
      watcher?.cancel()
      watcher = CoroutineScope(Dispatchers.Main.immediate).launch {
        Scale.state.collect { state ->
          // One event JS could not take must not stop the ones after it.
          runCatching { sendEvent("onStateChange", state.toMap()) }
        }
      }
    }

    OnStopObserving("onStateChange") {
      watcher?.cancel()
      watcher = null
    }

    lateinit var healthPermission: AppContextActivityResultLauncher<HealthPermissionRequest, Set<String>>

    RegisterActivityContracts {
      healthPermission = registerForActivityResult(HealthPermissionContract())
    }

    Function("getState") { Scale.state.value.toMap() }

    Function("hasPermissions") { Scale.hasPermissions(context) }

    AsyncFunction("requestPermissions") { promise: Promise ->
      val permissions = appContext.permissions ?: throw Exceptions.PermissionsModuleNotFound()
      permissions.askForPermissions({ promise.resolve(Scale.hasPermissions(context)) }, *Scale.bluetoothPermissions)
    }

    Function("start") {
      val context = context
      main.post { Scale.start(context) }
    }

    Function("stop") { main.post { Scale.stop() } }

    Function("forget") {
      val context = context
      main.post { Scale.forget(context) }
    }

    Function("requestBluetooth") {
      if (Scale.hasPermissions(context)) startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    Function("openAppSettings") {
      startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    /** Android 13's per-app language setting; earlier versions only follow the phone. */
    Function("canChooseLanguage") { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }

    Function("openLanguageSettings") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
      }
    }

    Function("healthStatus") { Health.status(context) }

    AsyncFunction("healthPermitted") Coroutine { -> Health.permitted(context) }

    AsyncFunction("requestHealthPermission") Coroutine { ->
      if (Health.permitted(context)) return@Coroutine true
      val granted = healthPermission.launch(HealthPermissionRequest(ArrayList(Health.permissions)))
      granted.containsAll(Health.permissions)
    }

    AsyncFunction("saveWeight") Coroutine { kilograms: Double, measuredAt: Double ->
      Health.save(context, kilograms, measuredAt.toLong())
    }

    AsyncFunction("deleteWeight") Coroutine { id: String -> Health.delete(context, id) }

    Function("openHealthConnect") {
      startActivity(if (Health.status(context) == "available") Health.settingsIntent() else Health.installIntent())
    }

    // Nothing is left to show the scale to, such as after a reload: let go of it.
    OnDestroy {
      watcher?.cancel()
      main.post { Scale.stop() }
    }
  }

  /** Opens a system screen. Some phones lack one (no Play Store, no per-app language): skip it. */
  private fun startActivity(intent: Intent) {
    val activity = appContext.currentActivity
    try {
      if (activity != null) {
        activity.startActivity(intent)
      } else {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    } catch (missing: ActivityNotFoundException) {
      // Nothing to open; the button stays where it is.
    }
  }
}

internal data class HealthPermissionRequest(val permissions: ArrayList<String>) : Serializable

/** Health Connect's permission screen, as an activity result Expo modules can wait for. */
internal class HealthPermissionContract :
  AppContextActivityResultContract<HealthPermissionRequest, Set<String>> {
  private val delegate = PermissionController.createRequestPermissionResultContract()

  override fun createIntent(context: Context, input: HealthPermissionRequest): Intent =
    delegate.createIntent(context, input.permissions.toSet())

  override fun parseResult(input: HealthPermissionRequest, resultCode: Int, intent: Intent?): Set<String> =
    delegate.parseResult(resultCode, intent)
}
