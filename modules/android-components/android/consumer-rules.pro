# Back.kt reads React Native's back callback by its name, so R8 must keep it in release builds.
-keepclassmembers class com.facebook.react.ReactActivity {
  private ** mBackPressedCallback;
}
