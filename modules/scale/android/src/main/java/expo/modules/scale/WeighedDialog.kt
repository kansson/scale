package expo.modules.scale

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The app's dynamic Material palette with expressive motion, so the dialog matches the app and the
 * wallpaper. Phones before Android 12 have no such palette and take Material's own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WeighedTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val dark = isSystemInDarkTheme()
  val colors = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
      if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    dark -> darkColorScheme()
    else -> lightColorScheme()
  }
  MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), content = content)
}

/**
 * What became of a weight taken in the background, as Material's alert dialog with an icon: the
 * icon and title centred over the text, and the buttons at the end. It lays itself out as
 * `AlertDialog` does and opens and closes with the system dialog's animation and dim, which a
 * see-through activity has to draw itself. [closing] animates it out, then [onClosed] follows.
 */
@Composable
internal fun WeighedDialog(
  weighed: Weighed,
  closing: Boolean,
  onClose: () -> Unit,
  onClosed: () -> Unit,
  onUndo: () -> Unit,
  onOpenApp: () -> Unit
) {
  val context = LocalContext.current
  val colors = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val haptics = LocalHapticFeedback.current
  val saved = weighed.outcome == Weighed.Outcome.SAVED
  val appName = remember { context.applicationInfo.loadLabel(context.packageManager).toString() }
  val weight = weighed.weight(context)
  val time = weighed.time(context)

  // Android's dialog animation (`popup_enter_material`, `popup_exit_material`): a fade with a rise
  // from 20 dp below on Material's standard curve, and out with a 10 dp rise on its accelerating one.
  val shown = remember { Animatable(0f) }
  LaunchedEffect(closing) {
    if (!closing) {
      shown.animateTo(1f, tween(ENTER_MS, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
    } else {
      shown.animateTo(0f, tween(EXIT_MS, easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)))
      onClosed()
    }
  }
  // Felt as the dialog arrives, the phone perhaps in a pocket: done, or not.
  LaunchedEffect(weighed) {
    haptics.performHapticFeedback(if (saved) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .fillMaxSize()
      .drawBehind { drawRect(Color.Black.copy(alpha = DIM * shown.value)) }
      .clickable(interactionSource = null, indication = null, onClick = onClose)
      // Centred between the status and navigation bars, as a dialog window is; the dim covers them.
      .systemBarsPadding()
      .padding(horizontal = 48.dp)
  ) {
    Surface(
      shape = AlertDialogDefaults.shape,
      color = AlertDialogDefaults.containerColor,
      tonalElevation = AlertDialogDefaults.TonalElevation,
      modifier = Modifier
        .widthIn(min = 280.dp, max = 560.dp)
        .graphicsLayer {
          alpha = shown.value
          val offset = with(density) { (if (closing) EXIT_RISE else ENTER_DROP).toPx() }
          translationY = offset * (1f - shown.value)
        }
        .semantics { paneTitle = context.getString(title(weighed.outcome)) }
        // Taps on the dialog itself stay in it.
        .clickable(interactionSource = null, indication = null, onClick = {})
    ) {
      // `AlertDialog`'s layout with an icon: 24 dp all round, the icon and title centred.
      Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
          painterResource(if (saved) R.drawable.scale_saved else R.drawable.scale_warning),
          contentDescription = null,
          tint = if (saved) colors.secondary else colors.error,
          modifier = Modifier.size(24.dp)
        )
        Text(
          stringResource(title(weighed.outcome)),
          style = MaterialTheme.typography.headlineSmall,
          color = AlertDialogDefaults.titleContentColor,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )
        Text(
          when (weighed.outcome) {
            Weighed.Outcome.SAVED -> stringResource(R.string.scale_saved_text, weight, time)
            Weighed.Outcome.NOT_ALLOWED -> stringResource(R.string.scale_not_allowed_text, weight, time, appName)
            Weighed.Outcome.FAILED -> stringResource(R.string.scale_failed_text, weight, time, appName)
          },
          style = MaterialTheme.typography.bodyMedium,
          color = AlertDialogDefaults.textContentColor,
          modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
        )
        Row(
          Modifier.fillMaxWidth().padding(top = 24.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
          if (saved) {
            if (weighed.recordId != null) {
              TextButton(onClick = onUndo) { Text(stringResource(R.string.scale_undo)) }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.scale_done)) }
          } else {
            TextButton(onClick = onClose) { Text(stringResource(R.string.scale_done)) }
            TextButton(onClick = onOpenApp) { Text(stringResource(R.string.scale_open, appName)) }
          }
        }
      }
    }
  }
}

private fun title(outcome: Weighed.Outcome) =
  if (outcome == Weighed.Outcome.SAVED) R.string.scale_saved_title else R.string.scale_failed_title

private const val ENTER_MS = 220
private const val EXIT_MS = 150
private val ENTER_DROP = 20.dp
private val EXIT_RISE = (-10).dp
private const val DIM = 0.6f
