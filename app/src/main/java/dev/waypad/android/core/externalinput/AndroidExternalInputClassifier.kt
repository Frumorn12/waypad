package dev.waypad.android.core.externalinput

import android.view.InputDevice

object AndroidExternalInputClassifier {
    fun classify(sources: Int, keyboardType: Int): Set<ExternalInputDeviceClass> {
        val classes = linkedSetOf<ExternalInputDeviceClass>()
        if (hasSource(sources, InputDevice.SOURCE_MOUSE) ||
            hasSource(sources, InputDevice.SOURCE_MOUSE_RELATIVE)
        ) {
            classes += ExternalInputDeviceClass.Mouse
        }
        if (hasSource(sources, InputDevice.SOURCE_TOUCHPAD)) {
            classes += ExternalInputDeviceClass.Touchpad
        }
        if (keyboardType != InputDevice.KEYBOARD_TYPE_NONE &&
            hasSource(sources, InputDevice.SOURCE_KEYBOARD)
        ) {
            classes += ExternalInputDeviceClass.Keyboard
        }
        if (hasSource(sources, InputDevice.SOURCE_GAMEPAD)) {
            classes += ExternalInputDeviceClass.Gamepad
        }
        if (hasSource(sources, InputDevice.SOURCE_JOYSTICK)) {
            classes += ExternalInputDeviceClass.Joystick
        }
        if (classes.isEmpty()) classes += ExternalInputDeviceClass.Unknown
        return classes
    }

    fun hasSource(sources: Int, source: Int): Boolean = sources and source == source
}
