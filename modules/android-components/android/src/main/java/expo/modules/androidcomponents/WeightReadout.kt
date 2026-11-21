package expo.modules.androidcomponents

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import expo.modules.kotlin.views.ComposeProps
import expo.modules.kotlin.views.FunctionalComposableScope
import expo.modules.kotlin.views.OptimizedComposeProps

@OptimizedComposeProps
data class WeightReadoutProps(
  val amount: String = "",
  /** Empty while the placeholder shows, as the unit is the scale's to report. */
  val unit: String = "",
  /**
   * `waiting` before there is a weight, when the amount is a placeholder; `settling` while the
   * scale still moves; `settled` once it has.
   */
  val state: String = "waiting"
) : ComposeProps

/**
 * A weight as large as the width allows, with its unit beside it. The figure carries the
 * weighing's state in its own strokes: light while the scale moves, heavy once it settles, and a
 * faint placeholder before anyone steps on. The figures are tabular, so digits change in place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FunctionalComposableScope.WeightReadoutContent(props: WeightReadoutProps) {
  val typography = MaterialTheme.typography
  val colors = MaterialTheme.colorScheme
  val motion = MaterialTheme.motionScheme
  val settled = props.state == "settled"
  val waiting = props.state == "waiting"

  val color by animateColorAsState(
    when {
      waiting -> colors.outlineVariant
      settled -> colors.onSurface
      else -> colors.onSurfaceVariant
    },
    motion.defaultEffectsSpec(),
    label = "weight-colour"
  )
  // The system face is variable from Android 12, so the strokes thicken smoothly; older phones
  // step between the weights they have.
  val strokes by animateIntAsState(
    // The placeholder is drawn heavy too, so its dashes read as a scale's blank display.
    if (props.state == "settling") MOVING_WEIGHT else SETTLED_WEIGHT,
    motion.slowEffectsSpec(),
    label = "weight-strokes"
  )

  // A tick in the hand as the scale settles; not when the screen opens on a settled weight.
  val haptics = LocalHapticFeedback.current
  var previous by remember { mutableStateOf(props.state) }
  LaunchedEffect(props.state) {
    if (previous == "settling" && settled) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    previous = props.state
  }

  // Read out as one phrase, such as "72.4 kg"; the placeholder says nothing, the dock speaks.
  val semantics = if (waiting) {
    Modifier.clearAndSetSemantics { }
  } else {
    Modifier.semantics(mergeDescendants = true) { }
  }

  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = semantics) {
    Text(
      props.amount,
      style = typography.displayLarge.copy(
        lineHeight = MAX_SIZE,
        fontWeight = FontWeight(strokes),
        fontFeatureSettings = "tnum",
        letterSpacing = (-2).sp
      ),
      autoSize = TextAutoSize.StepBased(minFontSize = MIN_SIZE, maxFontSize = MAX_SIZE, stepSize = 4.sp),
      color = color,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Clip,
      modifier = Modifier.alignByBaseline().weight(1f, fill = false)
    )
    if (props.unit.isNotEmpty()) {
      Text(
        props.unit,
        style = typography.headlineMedium,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.alignByBaseline()
      )
    }
  }
}

private const val MOVING_WEIGHT = 300
private const val SETTLED_WEIGHT = 800
private val MAX_SIZE = 168.sp
private val MIN_SIZE = 56.sp
