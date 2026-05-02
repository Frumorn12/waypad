package dev.waypad.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.waypad.android.core.externalinput.AndroidExternalInputClassifier
import dev.waypad.android.core.externalinput.ExternalInputDeviceClass
import dev.waypad.android.core.externalinput.AndroidExternalInputMapper
import dev.waypad.android.core.externalinput.ExternalInputDeviceMonitor
import dev.waypad.android.core.externalinput.ExternalInputEvent
import dev.waypad.android.ui.WaypadApp

class MainActivity : ComponentActivity() {
    private companion object {
        const val TAG = "WaypadExternalInput"
    }

    private val viewModel: WaypadViewModel by viewModels()
    private val externalInputMapper = AndroidExternalInputMapper()
    private lateinit var externalInputMonitor: ExternalInputDeviceMonitor
    private val pressedControllerKeys = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        externalInputMonitor = ExternalInputDeviceMonitor(
            context = this,
            mapper = externalInputMapper,
            onDevicesChanged = viewModel::updateExternalInputDevices,
        )
        window.decorView.setOnCapturedPointerListener { _, event ->
            routeExternalMotionEvent(event)
        }
        setContent {
            WaypadApp(viewModel)
        }
        handleInviteIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInviteIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        externalInputMonitor.start()
    }

    override fun onStop() {
        externalInputMonitor.stop()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleControllerRevealShortcut(event)) return true
        val external = externalInputMapper.keyEventToExternal(event)
        if (external != null && routeExternalInputEvent(external)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (routeExternalMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private fun routeExternalMotionEvent(event: MotionEvent): Boolean {
        val events = externalInputMapper.motionEventToExternal(event)
        if (events.isEmpty()) return false
        var handled = false
        for (external in events) {
            handled = viewModel.handleExternalInputEvent(external) || handled
        }
        if (handled) {
            Log.v(TAG, "external_motion_handled action=${event.actionMasked} count=${events.size}")
        }
        return handled
    }

    private fun routeExternalInputEvent(event: ExternalInputEvent): Boolean {
        val handled = viewModel.handleExternalInputEvent(event)
        if (handled) Log.v(TAG, "external_key_handled type=${event.deviceType.wireName}")
        return handled
    }

    private fun handleInviteIntent(intent: Intent?) {
        val data = intent?.dataString ?: return
        if (data.startsWith("waypad://invite")) {
            Log.i(TAG, "qr_invite_intent")
            viewModel.applyInvite(data)
            intent.data = null
        }
    }

    private fun handleControllerRevealShortcut(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        if (!device.isExternal) return false
        val classes = AndroidExternalInputClassifier.classify(device.sources, device.keyboardType)
        val isController = ExternalInputDeviceClass.Gamepad in classes ||
            ExternalInputDeviceClass.Joystick in classes
        if (!isController) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> pressedControllerKeys += event.keyCode
            KeyEvent.ACTION_UP -> pressedControllerKeys -= event.keyCode
        }

        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                viewModel.revealRemoteScreenControls("controller_mode")
            }
            return true
        }

        val selectStartCombo =
            KeyEvent.KEYCODE_BUTTON_SELECT in pressedControllerKeys &&
                KeyEvent.KEYCODE_BUTTON_START in pressedControllerKeys
        if (selectStartCombo) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                viewModel.revealRemoteScreenControls("controller_start_select")
            }
            return true
        }
        return false
    }
}
