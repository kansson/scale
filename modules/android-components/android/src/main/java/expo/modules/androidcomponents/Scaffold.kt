package expo.modules.androidcomponents

import android.graphics.Color
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import expo.modules.kotlin.views.ComposeProps
import expo.modules.kotlin.views.FunctionalComposableScope
import expo.modules.kotlin.views.OptimizedComposeProps
import expo.modules.ui.UIComposableScope
import expo.modules.ui.composeOrNull
import expo.modules.ui.findChildSlotView
import expo.modules.ui.isSlotView
import expo.modules.ui.renderSlot

// Shares the Scaffold's scroll behavior with the app bar rendered in its topBar slot, so the
// separate native views drive one Material collapse animation.
@OptIn(ExperimentalMaterial3Api::class)
val LocalTopAppBarScrollBehavior = staticCompositionLocalOf<TopAppBarScrollBehavior?> { null }

@OptimizedComposeProps
data class ScaffoldProps(
  // One of: "none", "pinned", "enterAlways", "exitUntilCollapsed".
  val scrollBehavior: String = "none",
  // Screens above the app's navigation bar must not reserve the system navigation inset again.
  val bottomInset: Boolean = true,
  val snackbarId: Double = 0.0,
  val snackbarMessage: String = "",
  // Replaces the `surface` background, such as transparent where a surface behind already paints.
  val containerColor: Color? = null
) : ComposeProps

@OptimizedComposeProps
data class AppBarProps(
  val title: String = "",
  val containerColor: Color? = null
) : ComposeProps

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionalComposableScope.ScaffoldContent(props: ScaffoldProps, onSnackbarDismiss: () -> Unit) {
  val topBarSlotView = findChildSlotView(view, "topBar")
  val bottomBarSlotView = findChildSlotView(view, "bottomBar")
  val floatingActionButtonSlotView = findChildSlotView(view, "floatingActionButton")
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(props.snackbarId) {
    if (props.snackbarId > 0.0 && props.snackbarMessage.isNotEmpty()) {
      snackbarHostState.showSnackbar(
        message = props.snackbarMessage,
        withDismissAction = true,
        duration = SnackbarDuration.Short
      )
      onSnackbarDismiss()
    }
  }

  val scrollBehavior: TopAppBarScrollBehavior? = when (props.scrollBehavior) {
    "pinned" -> TopAppBarDefaults.pinnedScrollBehavior()
    "enterAlways" -> TopAppBarDefaults.enterAlwaysScrollBehavior()
    "exitUntilCollapsed" -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    else -> null
  }

  Scaffold(
    modifier = if (scrollBehavior != null) {
      Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    } else {
      Modifier
    },
    // Material 3 screens sit on `surface`; `background` is the legacy role.
    containerColor = props.containerColor.composeOrNull ?: MaterialTheme.colorScheme.surface,
    contentWindowInsets = if (props.bottomInset) {
      ScaffoldDefaults.contentWindowInsets
    } else {
      ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    },
    topBar = {
      CompositionLocalProvider(LocalTopAppBarScrollBehavior provides scrollBehavior) {
        topBarSlotView?.renderSlot()
      }
    },
    bottomBar = {
      bottomBarSlotView?.renderSlot()
    },
    floatingActionButton = {
      floatingActionButtonSlotView?.renderSlot()
    },
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState)
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
    ) {
      Children(UIComposableScope(), filter = { !isSlotView(it) })
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionalComposableScope.TopAppBarContent(props: AppBarProps, large: Boolean) {
  // Slot lookups read the view hierarchy directly. Registering this scope through `Children` (which
  // draws nothing here) makes slots attached after the first composition, such as actions that
  // appear once a screen has loaded, recompose the bar.
  Children(UIComposableScope(), filter = { false })

  val titleSlotView = findChildSlotView(view, "title")
  val navigationIconSlotView = findChildSlotView(view, "navigationIcon")
  val actionsSlotView = findChildSlotView(view, "actions")
  val behavior = LocalTopAppBarScrollBehavior.current
  val colors = barColors(
    TopAppBarDefaults.topAppBarColors(
      containerColor = props.containerColor.composeOrNull ?: MaterialTheme.colorScheme.surface
    ),
    behavior?.state
  )
  val title: @Composable () -> Unit = {
    if (titleSlotView != null) {
      titleSlotView.renderSlot()
    } else {
      Text(props.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }

  // Rebuilt each time its screen comes back (`rememberAttachCount`); the scroll state is the
  // Scaffold's and is kept.
  key(rememberAttachCount()) {
    if (large) {
      LargeTopAppBar(
        title = title,
        navigationIcon = { navigationIconSlotView?.renderSlot() },
        actions = { actionsSlotView?.renderSlot() },
        colors = colors,
        scrollBehavior = behavior
      )
    } else {
      TopAppBar(
        title = title,
        navigationIcon = { navigationIconSlotView?.renderSlot() },
        actions = { actionsSlotView?.renderSlot() },
        colors = colors,
        scrollBehavior = behavior
      )
    }
  }
}

/**
 * Counts the times this composition's view is attached to the window again. A screen's view is
 * detached while another screen covers it, and Compose drops the draw-phase state reads it
 * tracked. Material's bars set their height offset, paint their container colour and fade their
 * titles in layout and draw-phase reads, so a screen that came back with a collapsed bar kept it
 * collapsed however far its content scrolled back: the state changed but nothing redrew. Keying a
 * bar on this count rebuilds it, reads and all, each time its screen returns (as listan's bars do,
 * D-1469).
 */
@Composable
private fun rememberAttachCount(): Int {
  val view = LocalView.current
  var count by remember(view) { mutableIntStateOf(0) }
  DisposableEffect(view) {
    val listener = object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(attached: View) {
        count++
      }

      override fun onViewDetachedFromWindow(detached: View) = Unit
    }
    view.addOnAttachStateChangeListener(listener)
    onDispose { view.removeOnAttachStateChangeListener(listener) }
  }
  return count
}

/** Whether the bar has fully collapsed, not while it is still moving. */
@OptIn(ExperimentalMaterial3Api::class)
private fun TopAppBarState.collapsed() = heightOffsetLimit != 0f && collapsedFraction > 0.99f

/**
 * Material's bar colours, scrolled once the bar has fully collapsed. Material reads only content
 * scrolled beneath the bar, so a collapsed bar over content that starts at the top (a screen
 * returned to, its list restored, or a drag the bar consumed whole) would show its resting colour
 * until the list itself scrolled.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun barColors(base: TopAppBarColors, state: TopAppBarState?): TopAppBarColors {
  val target by remember(state) { derivedStateOf { if (state?.collapsed() == true) 1f else 0f } }
  // Match TopAppBar's own container animation.
  val collapsed by animateFloatAsState(
    target,
    MaterialTheme.motionScheme.defaultEffectsSpec(),
    label = "collapsed"
  )
  return base.copy(containerColor = lerp(base.containerColor, base.scrolledContainerColor, collapsed))
}
