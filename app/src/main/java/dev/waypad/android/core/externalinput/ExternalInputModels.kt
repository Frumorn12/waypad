package dev.waypad.android.core.externalinput

import dev.waypad.android.core.model.ButtonState

enum class ExternalInputDeviceClass(val wireName: String) {
    Keyboard("keyboard"),
    Mouse("mouse"),
    Touchpad("touchpad"),
    Gamepad("gamepad"),
    Joystick("joystick"),
    Unknown("unknown"),
}

data class ExternalInputDeviceSummary(
    val id: String,
    val androidId: Int,
    val name: String,
    val descriptor: String?,
    val classes: Set<ExternalInputDeviceClass>,
    val sources: Int,
    val isExternal: Boolean,
) {
    val displayClasses: String =
        classes.sortedBy { it.ordinal }.joinToString(", ") { it.wireName }
}

sealed interface ExternalInputEvent {
    val deviceId: String
    val deviceType: ExternalInputDeviceClass
    val highFrequency: Boolean
        get() = false

    data class DeviceConnected(
        override val deviceId: String,
        override val deviceType: ExternalInputDeviceClass,
        val name: String,
        val classes: Set<ExternalInputDeviceClass>,
    ) : ExternalInputEvent

    data class DeviceDisconnected(
        override val deviceId: String,
        override val deviceType: ExternalInputDeviceClass,
    ) : ExternalInputEvent

    data class PointerMove(
        override val deviceId: String,
        override val deviceType: ExternalInputDeviceClass,
        val dx: Float,
        val dy: Float,
    ) : ExternalInputEvent {
        override val highFrequency: Boolean = true
    }

    data class PointerButton(
        override val deviceId: String,
        override val deviceType: ExternalInputDeviceClass,
        val button: dev.waypad.android.core.model.PointerButton,
        val state: ButtonState,
    ) : ExternalInputEvent

    data class PointerScroll(
        override val deviceId: String,
        override val deviceType: ExternalInputDeviceClass,
        val dx: Float,
        val dy: Float,
        val finish: Boolean,
    ) : ExternalInputEvent {
        override val highFrequency: Boolean = !finish
    }

    data class KeyboardKey(
        override val deviceId: String,
        override val deviceType: ExternalInputDeviceClass,
        val keysym: Int,
        val state: ButtonState,
        val repeat: Boolean,
    ) : ExternalInputEvent

    data class ControllerButton(
        override val deviceId: String,
        override val deviceType: ExternalInputDeviceClass,
        val button: String,
        val state: ButtonState,
    ) : ExternalInputEvent

    data class ControllerAxis(
        override val deviceId: String,
        override val deviceType: ExternalInputDeviceClass,
        val axis: String,
        val value: Float,
    ) : ExternalInputEvent {
        override val highFrequency: Boolean = true
    }
}

fun Set<ExternalInputDeviceClass>.primaryExternalType(): ExternalInputDeviceClass =
    when {
        ExternalInputDeviceClass.Mouse in this -> ExternalInputDeviceClass.Mouse
        ExternalInputDeviceClass.Touchpad in this -> ExternalInputDeviceClass.Touchpad
        ExternalInputDeviceClass.Gamepad in this -> ExternalInputDeviceClass.Gamepad
        ExternalInputDeviceClass.Joystick in this -> ExternalInputDeviceClass.Joystick
        ExternalInputDeviceClass.Keyboard in this -> ExternalInputDeviceClass.Keyboard
        else -> ExternalInputDeviceClass.Unknown
    }
