package dev.waypad.android.ui.system

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Window-level side effects owned by the app shell.
 *
 * Moved out of `WaypadApp.kt` unchanged: the logging tags and the exact ordering of the window flag
 * writes are relied upon by the field diagnostics.
 */

/** Grabs the physical mouse/touchpad so its deltas can be forwarded to the host. */
@Composable
fun ExternalPointerCaptureEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (enabled) {
                Log.i("WaypadExternalInput", "pointer_capture_request")
                view.requestPointerCapture()
            } else if (view.hasPointerCapture()) {
                Log.i("WaypadExternalInput", "pointer_capture_release")
                view.releasePointerCapture()
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && view.hasPointerCapture()) {
                view.releasePointerCapture()
            }
        }
    }
}

/** Lets the remote display rotate freely, restoring the previous policy on exit. */
@Composable
fun RemoteScreenOrientationEffect(onRemoteDisplay: Boolean, fullscreen: Boolean) {
    val view = LocalView.current
    DisposableEffect(onRemoteDisplay, fullscreen, view) {
        val activity = view.context.findActivity()
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val policy = when {
            // A desktop is 16:9 and fullscreen exists to give it the whole display. Following the
            // sensor here would let the phone sit in portrait and waste three quarters of the
            // screen on letterboxing, so landscape is requested outright — but either way up, so
            // the phone can still be turned around.
            fullscreen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            // Windowed mode has the setup card and the controls to read, so the sensor decides.
            onRemoteDisplay -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        Log.i("WaypadRemoteScreen", "orientation_policy=$policy fullscreen=$fullscreen")
        activity?.requestedOrientation = policy
        onDispose {
            activity?.requestedOrientation = previous
        }
    }
}

/** Hides/shows the system bars for the immersive remote display. */
@Composable
fun FullscreenSystemUiEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = view.context.findActivity()?.window
        if (enabled) {
            Log.i("WaypadRemoteScreen", "fullscreen_system_ui_hide")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.insetsController?.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window?.insetsController?.hide(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility =
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        } else {
            Log.i("WaypadRemoteScreen", "fullscreen_system_ui_show")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window?.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
