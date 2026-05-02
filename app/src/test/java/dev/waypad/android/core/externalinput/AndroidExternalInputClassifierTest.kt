package dev.waypad.android.core.externalinput

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidExternalInputClassifierTest {
    @Test
    fun classifiesKeyboardMouseAndGamepadSources() {
        val keyboard = AndroidExternalInputClassifier.classify(
            InputDevice.SOURCE_KEYBOARD,
            InputDevice.KEYBOARD_TYPE_ALPHABETIC,
        )
        assertTrue(ExternalInputDeviceClass.Keyboard in keyboard)

        val mouse = AndroidExternalInputClassifier.classify(
            InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_KEYBOARD,
            InputDevice.KEYBOARD_TYPE_NONE,
        )
        assertTrue(ExternalInputDeviceClass.Mouse in mouse)

        val controller = AndroidExternalInputClassifier.classify(
            InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK,
            InputDevice.KEYBOARD_TYPE_NONE,
        )
        assertTrue(ExternalInputDeviceClass.Gamepad in controller)
        assertTrue(ExternalInputDeviceClass.Joystick in controller)
    }

    @Test
    fun mapsCommonAndroidKeysToXkbKeysyms() {
        assertEquals('a'.code, AndroidKeySymMapper.keysymFor(KeyEvent.KEYCODE_A))
        assertEquals(0xffe3, AndroidKeySymMapper.keysymFor(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(0xff51, AndroidKeySymMapper.keysymFor(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(0xffbe, AndroidKeySymMapper.keysymFor(KeyEvent.KEYCODE_F1))
        assertEquals("left_shoulder", AndroidKeySymMapper.controllerButtonName(KeyEvent.KEYCODE_BUTTON_L1))
    }

    @Test
    fun appliesJoystickDeadzoneAndClampsAxes() {
        assertEquals(0f, AndroidExternalInputMapper.normalizeAxis(0.05f, 0.1f))
        assertEquals(0.5f, AndroidExternalInputMapper.normalizeAxis(0.5f, 0.1f))
        assertEquals(1f, AndroidExternalInputMapper.normalizeAxis(1.4f, 0.1f))
        assertEquals("left_x", AndroidExternalInputMapper.axisName(MotionEvent.AXIS_X))
    }

    @Test
    fun prioritizesControllerTypeForCompositeGamepads() {
        val classes = setOf(
            ExternalInputDeviceClass.Keyboard,
            ExternalInputDeviceClass.Gamepad,
            ExternalInputDeviceClass.Joystick,
        )
        assertEquals(ExternalInputDeviceClass.Gamepad, classes.primaryExternalType())
    }
}
