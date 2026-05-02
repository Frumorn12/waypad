package dev.waypad.android.core.externalinput

import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.waypad.android.core.model.ButtonState
import dev.waypad.android.core.model.PointerButton
import kotlin.math.abs

private const val TAG = "WaypadExternalInput"
private const val WHEEL_SCALE = 48f
private const val POINTER_EPSILON = 0.01f
private const val AXIS_EPSILON = 0.04f
private const val DEFAULT_AXIS_DEADZONE = 0.12f

class AndroidExternalInputMapper {
    private val lastPointerPosition = mutableMapOf<Int, Pair<Float, Float>>()
    private val lastAxisValues = mutableMapOf<String, Float>()

    fun deviceSummary(device: InputDevice): ExternalInputDeviceSummary {
        val classes = AndroidExternalInputClassifier.classify(device.sources, device.keyboardType)
        return ExternalInputDeviceSummary(
            id = stableDeviceId(device),
            androidId = device.id,
            name = device.name ?: "Android input ${device.id}",
            descriptor = device.descriptor,
            classes = classes,
            sources = device.sources,
            isExternal = device.isExternal,
        )
    }

    fun keyEventToExternal(event: KeyEvent): ExternalInputEvent? {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return null
        val device = event.device ?: return null
        val summary = deviceSummary(device)
        if (!summary.isExternal) return null
        val state = if (event.action == KeyEvent.ACTION_DOWN) ButtonState.Pressed else ButtonState.Released
        val repeat = event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0
        val isController = summary.classes.any {
            it == ExternalInputDeviceClass.Gamepad || it == ExternalInputDeviceClass.Joystick
        }
        if (isController) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) return null
            AndroidKeySymMapper.controllerButtonName(event.keyCode)?.let { button ->
                return ExternalInputEvent.ControllerButton(
                    deviceId = summary.id,
                    deviceType = summary.classes.primaryExternalType(),
                    button = button,
                    state = state,
                )
            }
            return null
        }
        val keysym = AndroidKeySymMapper.keysymFor(event) ?: return null
        if (ExternalInputDeviceClass.Keyboard !in summary.classes) return null
        return ExternalInputEvent.KeyboardKey(
            deviceId = summary.id,
            deviceType = ExternalInputDeviceClass.Keyboard,
            keysym = keysym,
            state = state,
            repeat = repeat,
        )
    }

    fun motionEventToExternal(event: MotionEvent): List<ExternalInputEvent> {
        val device = event.device ?: return emptyList()
        val summary = deviceSummary(device)
        if (!summary.isExternal) return emptyList()
        return when {
            summary.classes.any { it == ExternalInputDeviceClass.Mouse || it == ExternalInputDeviceClass.Touchpad } ->
                mapPointerMotion(summary, event)
            summary.classes.any { it == ExternalInputDeviceClass.Gamepad || it == ExternalInputDeviceClass.Joystick } ->
                mapControllerMotion(summary, device, event)
            else -> emptyList()
        }
    }

    fun clearDevice(androidDeviceId: Int) {
        lastPointerPosition.remove(androidDeviceId)
        lastAxisValues.keys.removeAll { it.startsWith("$androidDeviceId:") }
    }

    private fun mapPointerMotion(
        summary: ExternalInputDeviceSummary,
        event: MotionEvent,
    ): List<ExternalInputEvent> {
        val type = if (ExternalInputDeviceClass.Mouse in summary.classes) {
            ExternalInputDeviceClass.Mouse
        } else {
            ExternalInputDeviceClass.Touchpad
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_SCROLL -> {
                val dx = event.getAxisValue(MotionEvent.AXIS_HSCROLL) * WHEEL_SCALE
                val dy = -event.getAxisValue(MotionEvent.AXIS_VSCROLL) * WHEEL_SCALE
                if (abs(dx) < POINTER_EPSILON && abs(dy) < POINTER_EPSILON) {
                    emptyList()
                } else {
                    listOf(ExternalInputEvent.PointerScroll(summary.id, type, dx, dy, finish = true))
                }
            }
            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                val button = pointerButton(event.actionButton) ?: return emptyList()
                val state = if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
                    ButtonState.Pressed
                } else {
                    ButtonState.Released
                }
                listOf(ExternalInputEvent.PointerButton(summary.id, type, button, state))
            }
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE -> {
                val relativeX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                val relativeY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                val dx: Float
                val dy: Float
                if (abs(relativeX) > POINTER_EPSILON || abs(relativeY) > POINTER_EPSILON) {
                    dx = relativeX
                    dy = relativeY
                } else {
                    val previous = lastPointerPosition[event.deviceId]
                    lastPointerPosition[event.deviceId] = event.x to event.y
                    if (previous == null) return emptyList()
                    dx = event.x - previous.first
                    dy = event.y - previous.second
                }
                if (abs(dx) < POINTER_EPSILON && abs(dy) < POINTER_EPSILON) {
                    emptyList()
                } else {
                    listOf(ExternalInputEvent.PointerMove(summary.id, type, dx, dy))
                }
            }
            else -> emptyList()
        }
    }

    private fun mapControllerMotion(
        summary: ExternalInputDeviceSummary,
        device: InputDevice,
        event: MotionEvent,
    ): List<ExternalInputEvent> {
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return emptyList()
        val type = if (ExternalInputDeviceClass.Gamepad in summary.classes) {
            ExternalInputDeviceClass.Gamepad
        } else {
            ExternalInputDeviceClass.Joystick
        }
        return CONTROLLER_AXES.mapNotNull { axis ->
            val raw = event.getAxisValue(axis)
            val flat = device.getMotionRange(axis, event.source)?.flat ?: DEFAULT_AXIS_DEADZONE
            val normalized = normalizeAxis(raw, flat)
            val axisName = axisName(axis)
            val key = "${event.deviceId}:$axisName"
            val previous = lastAxisValues[key]
            if (previous != null && abs(previous - normalized) < AXIS_EPSILON) return@mapNotNull null
            if (previous == null && normalized == 0f) return@mapNotNull null
            lastAxisValues[key] = normalized
            ExternalInputEvent.ControllerAxis(summary.id, type, axisName, normalized)
        }
    }

    private fun pointerButton(actionButton: Int): PointerButton? = when (actionButton) {
        MotionEvent.BUTTON_PRIMARY -> PointerButton.Left
        MotionEvent.BUTTON_SECONDARY -> PointerButton.Right
        MotionEvent.BUTTON_TERTIARY -> PointerButton.Middle
        else -> {
            Log.d(TAG, "unsupported_pointer_button action_button=$actionButton")
            null
        }
    }

    private fun stableDeviceId(device: InputDevice): String {
        val descriptor = device.descriptor?.takeIf { it.isNotBlank() }
        return if (descriptor != null) {
            "android:${descriptor.hashCode().toUInt().toString(16)}"
        } else {
            "android:id-${device.id}"
        }
    }

    companion object {
        private val CONTROLLER_AXES = listOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y,
        )

        fun normalizeAxis(value: Float, flat: Float): Float {
            val deadzone = flat.coerceAtLeast(DEFAULT_AXIS_DEADZONE)
            if (abs(value) <= deadzone) return 0f
            return value.coerceIn(-1f, 1f)
        }

        fun axisName(axis: Int): String = when (axis) {
            MotionEvent.AXIS_X -> "left_x"
            MotionEvent.AXIS_Y -> "left_y"
            MotionEvent.AXIS_Z -> "right_x"
            MotionEvent.AXIS_RZ -> "right_y"
            MotionEvent.AXIS_LTRIGGER -> "left_trigger"
            MotionEvent.AXIS_RTRIGGER -> "right_trigger"
            MotionEvent.AXIS_HAT_X -> "hat_x"
            MotionEvent.AXIS_HAT_Y -> "hat_y"
            else -> "axis_$axis"
        }
    }
}
