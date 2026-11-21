package expo.modules.androidcomponents

import android.view.View
import com.facebook.react.ReactActivity
import expo.modules.kotlin.Promise
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.ui.ExpoUIView

class AndroidComponentsModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("AndroidComponents")

    // A committed back swipe is about to dismiss the top screen natively.
    Events("onNativeDismissal")

    OnCreate {
      StackBack.onDismissing = { sendEvent("onNativeDismissal", emptyMap<String, Any>()) }
    }

    OnDestroy {
      StackBack.onDismissing = null
    }

    // At the root screen the system takes Back, for its predictive back-to-home; elsewhere React
    // Native does, and stack screens get the system's cross-activity predictive back.
    Function("setReactNativeBack") { enabled: Boolean ->
      val activity = appContext.currentActivity as? ReactActivity ?: return@Function false
      setReactNativeBack(activity, enabled)
      activity.runOnUiThread {
        StackEnter.register(activity)
        StackBack.of(activity).setWanted(enabled)
      }
      true
    }

    // Back through the activity's dispatcher, so every registered back handler still runs.
    Function("navigateBack") {
      val activity = appContext.currentActivity as? ReactActivity ?: return@Function false
      activity.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
      true
    }

    // A closing screen keeps its last frame through the stack's exit transition.
    AsyncFunction("freezeScreen") { tag: Int, promise: Promise ->
      val activity = appContext.currentActivity
      val view = appContext.findView<View>(tag)
      if (activity == null || view == null) return@AsyncFunction promise.resolve(false)
      ScreenFreeze.freeze(activity, view) { promise.resolve(it) }
    }.runOnQueue(Queues.MAIN)

    // A predictive back removes its screen natively first; such a screen has nothing to freeze.
    Function("takeNativeDismissal") { StackBack.takeNativeDismissal() }

    AsyncFunction("thawScreen") { tag: Int ->
      appContext.findView<View>(tag)?.let { ScreenFreeze.thaw(it) }
    }.runOnQueue(Queues.MAIN)

    ExpoUIView<ScaffoldProps>("ScaffoldView") {
      val onSnackbarDismiss by Event<Unit>()

      Content { props ->
        ScaffoldContent(props) { onSnackbarDismiss(Unit) }
      }
    }

    ExpoUIView<AppBarProps>("TopAppBarView") {
      Content { props ->
        TopAppBarContent(props, large = false)
      }
    }

    ExpoUIView<AppBarProps>("LargeTopAppBarView") {
      Content { props ->
        TopAppBarContent(props, large = true)
      }
    }

    ExpoUIView<WeightReadoutProps>("WeightReadoutView") {
      Content { props ->
        WeightReadoutContent(props)
      }
    }

    ExpoUIView<NavigationBarSpacerProps>("NavigationBarSpacerView") {
      Content { _ ->
        NavigationBarSpacerContent()
      }
    }
  }
}
