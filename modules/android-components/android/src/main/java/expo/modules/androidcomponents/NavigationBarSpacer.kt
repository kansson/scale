package expo.modules.androidcomponents

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import expo.modules.kotlin.views.ComposeProps
import expo.modules.kotlin.views.FunctionalComposableScope
import expo.modules.kotlin.views.OptimizedComposeProps

@OptimizedComposeProps
class NavigationBarSpacerProps : ComposeProps

/**
 * As tall as the system navigation bar, so a surface drawn to the bottom edge of the screen keeps
 * its content above the gesture handle or the buttons.
 */
@Composable
fun FunctionalComposableScope.NavigationBarSpacerContent() {
  Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
}
