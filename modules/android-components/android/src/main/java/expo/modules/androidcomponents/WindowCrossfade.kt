package expo.modules.androidcomponents

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import android.view.ViewGroup
import android.view.animation.PathInterpolator

/**
 * Crossfades the whole window from how it looks now to how it looks after a change, such as a new
 * theme. Its colours change in Compose, in the palette React reads and in React Native's own views,
 * none of which animate; a picture of the window laid over it and faded out moves them all at once.
 */
internal object WindowCrossfade {
  private val main = Handler(Looper.getMainLooper())
  private var cover: Cover? = null

  private class Cover(val root: ViewGroup, val drawable: BitmapDrawable) {
    var waiting = false
    var animator: ValueAnimator? = null
  }

  /** Lays a picture of the window over it; [done] says whether it is in place. */
  fun cover(activity: Activity, done: (Boolean) -> Unit) {
    val root = activity.window.decorView as? ViewGroup ?: return done(false)
    val width = root.width
    val height = root.height
    if (!root.isAttachedToWindow || width == 0 || height == 0) return done(false)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(activity.window, bitmap, { result ->
      if (result != PixelCopy.SUCCESS || !root.isAttachedToWindow) {
        bitmap.recycle()
        return@request done(false)
      }
      remove()
      val drawable = BitmapDrawable(root.resources, bitmap).apply { setBounds(0, 0, width, height) }
      root.overlay.add(drawable)
      cover = Cover(root, drawable)
      main.postDelayed(::remove, LIMIT_MS)
      done(true)
    }, main)
  }

  /**
   * Fades the picture out. Called once React has committed the change; its views and Compose
   * redraw over the next few frames, so the fade waits for those first.
   */
  fun uncover() {
    val current = cover ?: return
    if (current.animator != null || current.waiting) return
    current.waiting = true
    afterFrames(SETTLE_FRAMES) {
      if (cover !== current) return@afterFrames
      current.animator = ValueAnimator.ofInt(255, 0).apply {
        duration = FADE_MS
        // Material's standard easing.
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener {
          current.drawable.alpha = it.animatedValue as Int
          current.root.invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
          override fun onAnimationEnd(animation: Animator) {
            if (cover === current) remove()
          }
        })
        start()
      }
    }
  }

  private fun afterFrames(count: Int, action: () -> Unit) {
    if (count == 0) return action()
    Choreographer.getInstance().postFrameCallback { afterFrames(count - 1, action) }
  }

  private fun remove() {
    val current = cover ?: return
    cover = null
    main.removeCallbacksAndMessages(null)
    current.animator?.cancel()
    current.root.overlay.remove(current.drawable)
    current.drawable.bitmap.recycle()
  }

  /** React's views, then Compose, redraw the new colours within a few frames of the commit. */
  private const val SETTLE_FRAMES = 4
  /** Material's medium duration, for a change across the whole screen. */
  private const val FADE_MS = 350L
  /** Never leaves a stale picture over the app, whatever happens to the change. */
  private const val LIMIT_MS = 3_000L
}
