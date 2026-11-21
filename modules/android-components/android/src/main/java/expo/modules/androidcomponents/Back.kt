package expo.modules.androidcomponents

import androidx.activity.OnBackPressedCallback
import com.facebook.react.ReactActivity
import com.facebook.react.modules.systeminfo.ReactNativeVersion

/**
 * React Native registers one always-enabled back callback, which hides Android's predictive
 * back-to-home animation. At the root screen the app disables it so the system owns Back.
 * The field is private; pin the React Native version that was reviewed.
 */
private const val REVIEWED_REACT_NATIVE = "0.88.1"

private val callbackField by lazy {
  val version = ReactNativeVersion.VERSION
  val actual = "${version["major"]}.${version["minor"]}.${version["patch"]}"
  check(actual == REVIEWED_REACT_NATIVE) {
    "React Native $actual: review ReactActivity.mBackPressedCallback before upgrading (reviewed $REVIEWED_REACT_NATIVE)"
  }
  ReactActivity::class.java.getDeclaredField("mBackPressedCallback").apply {
    check(OnBackPressedCallback::class.java.isAssignableFrom(type)) { "Unexpected back callback type $type" }
    isAccessible = true
  }
}

internal fun setReactNativeBack(activity: ReactActivity, enabled: Boolean) {
  val callback = callbackField.get(activity) as? OnBackPressedCallback
    ?: error("ReactActivity has no back callback")
  activity.runOnUiThread {
    callback.isEnabled = enabled
    check(callback.isEnabled == enabled) { "React Native's back callback ignored isEnabled = $enabled" }
  }
}
