package expo.modules.scale

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A weight the background weighing took, and what became of it in Health Connect. */
internal data class Weighed(
  val outcome: Outcome,
  val value: Double,
  val unit: WeightUnit,
  val measuredAt: Long,
  /** Health Connect's id for the weight, when it was saved. */
  val recordId: String?,
  val scaleName: String?
) {
  enum class Outcome {
    SAVED,
    /** Health Connect is missing, or does not allow the app to write. */
    NOT_ALLOWED,
    FAILED
  }

  fun into(intent: Intent): Intent = intent
    .putExtra(EXTRA_OUTCOME, outcome.name)
    .putExtra(EXTRA_VALUE, value)
    .putExtra(EXTRA_UNIT, unit.id)
    .putExtra(EXTRA_MEASURED_AT, measuredAt)
    .putExtra(EXTRA_RECORD, recordId)
    .putExtra(EXTRA_SCALE, scaleName)

  /** The weight as the scale shows it: tenths, the locale's decimal mark, and the unit's symbol. */
  fun weight(context: Context): String {
    val format = NumberFormat.getNumberInstance(context.resources.configuration.locales[0]).apply {
      minimumFractionDigits = 1
      maximumFractionDigits = 1
    }
    val symbol = if (unit == WeightUnit.CATTY) "斤" else unit.id
    return "${format.format(value)} $symbol"
  }

  fun time(context: Context): String = DateFormat.getTimeFormat(context).format(Date(measuredAt))

  companion object {
    private const val EXTRA_OUTCOME = "expo.modules.scale.OUTCOME"
    private const val EXTRA_VALUE = "expo.modules.scale.VALUE"
    private const val EXTRA_UNIT = "expo.modules.scale.UNIT"
    private const val EXTRA_MEASURED_AT = "expo.modules.scale.MEASURED_AT"
    private const val EXTRA_RECORD = "expo.modules.scale.RECORD"
    private const val EXTRA_SCALE = "expo.modules.scale.SCALE"

    fun from(intent: Intent?): Weighed? {
      intent ?: return null
      val outcome = Outcome.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_OUTCOME) } ?: return null
      val unit = WeightUnit.entries.firstOrNull { it.id == intent.getStringExtra(EXTRA_UNIT) } ?: return null
      return Weighed(
        outcome,
        intent.getDoubleExtra(EXTRA_VALUE, 0.0),
        unit,
        intent.getLongExtra(EXTRA_MEASURED_AT, 0L),
        intent.getStringExtra(EXTRA_RECORD),
        intent.getStringExtra(EXTRA_SCALE)
      )
    }
  }
}

/**
 * Says what became of a weight the background weighing took, in Material's alert dialog over
 * whatever is on screen, the lock screen too, as the app's own dialogs look. Done, Back or a tap
 * outside closes it; Undo removes the weight from Health Connect, once the phone is unlocked. Its
 * own task, out of Recents, so closing it goes back to what was there. Being a companion app
 * ([ScaleWake]) is what lets the background weighing open it.
 */
class WeighedActivity : ComponentActivity() {
  private var weighed by mutableStateOf<Weighed?>(null)
  private var closing by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    weighed = Weighed.from(intent) ?: return finishQuietly()
    // Seen on picking the phone up; the screen is not woken for it.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
    onBackPressedDispatcher.addCallback(this) { closing = true }
    setContentView(
      ComposeView(this).apply {
        setContent {
          val shown = weighed ?: return@setContent
          WeighedTheme {
            WeighedDialog(
              weighed = shown,
              closing = closing,
              onClose = { closing = true },
              onClosed = ::finishQuietly,
              onUndo = ::undo,
              onOpenApp = ::openApp
            )
          }
        }
      }
    )
  }

  /** Another weight while this one shows, such as someone else's: it shows that instead. */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    Weighed.from(intent)?.let {
      weighed = it
      closing = false
    }
  }

  // Home: the dialog goes with the screen it was over.
  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    finishQuietly()
  }

  private fun undo() {
    val recordId = weighed?.recordId ?: return
    val keyguard = getSystemService(KeyguardManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard?.isKeyguardLocked == true) {
      // Removing health data asks for the phone to be unlocked first.
      keyguard.requestDismissKeyguard(
        this,
        object : KeyguardManager.KeyguardDismissCallback() {
          override fun onDismissSucceeded() = undo(recordId)
        }
      )
    } else {
      undo(recordId)
    }
  }

  private fun undo(recordId: String) {
    val context = applicationContext
    closing = true
    // Not tied to this window, which closes meanwhile.
    background.launch {
      val removed = runCatching { Health.undo(context, recordId) }.isSuccess
      val message = if (removed) R.string.scale_undone else R.string.scale_undo_failed
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  private fun openApp() {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
    try {
      startActivity(launch)
    } catch (missing: ActivityNotFoundException) {
      // Nothing to open.
    }
    finishQuietly()
  }

  /** Closes without a window animation: the dialog has animated itself out already. */
  private fun finishQuietly() {
    finish()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
      @Suppress("DEPRECATION")
      overridePendingTransition(0, 0)
    }
  }

  internal companion object {
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun show(context: Context, weighed: Weighed) {
      val intent = weighed.into(Intent(context, WeighedActivity::class.java))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
      try {
        context.startActivity(intent)
      } catch (refused: RuntimeException) {
        // Android refused to open it from the background; the weight is saved all the same.
      }
    }
  }
}
