package dev.waypad.android.core.externalinput

import android.view.KeyEvent

object AndroidKeySymMapper {
    fun keysymFor(event: KeyEvent): Int? = keysymFor(event.keyCode)

    fun keysymFor(keyCode: Int): Int? {
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            return 'a'.code + keyCode - KeyEvent.KEYCODE_A
        }
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            return '0'.code + keyCode - KeyEvent.KEYCODE_0
        }
        if (keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            return 0xffbe + keyCode - KeyEvent.KEYCODE_F1
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_TAB -> 0xff09
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 0xff0d
            KeyEvent.KEYCODE_ESCAPE -> 0xff1b
            KeyEvent.KEYCODE_DEL -> 0xff08
            KeyEvent.KEYCODE_FORWARD_DEL -> 0xffff
            KeyEvent.KEYCODE_SPACE -> ' '.code
            KeyEvent.KEYCODE_DPAD_LEFT -> 0xff51
            KeyEvent.KEYCODE_DPAD_UP -> 0xff52
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0xff53
            KeyEvent.KEYCODE_DPAD_DOWN -> 0xff54
            KeyEvent.KEYCODE_INSERT -> 0xff63
            KeyEvent.KEYCODE_MOVE_HOME -> 0xff50
            KeyEvent.KEYCODE_MOVE_END -> 0xff57
            KeyEvent.KEYCODE_PAGE_UP -> 0xff55
            KeyEvent.KEYCODE_PAGE_DOWN -> 0xff56
            KeyEvent.KEYCODE_SHIFT_LEFT -> 0xffe1
            KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xffe2
            KeyEvent.KEYCODE_CTRL_LEFT -> 0xffe3
            KeyEvent.KEYCODE_CTRL_RIGHT -> 0xffe4
            KeyEvent.KEYCODE_CAPS_LOCK -> 0xffe5
            KeyEvent.KEYCODE_META_LEFT -> 0xffeb
            KeyEvent.KEYCODE_META_RIGHT -> 0xffec
            KeyEvent.KEYCODE_ALT_LEFT -> 0xffe9
            KeyEvent.KEYCODE_ALT_RIGHT -> 0xffea
            KeyEvent.KEYCODE_MINUS -> '-'.code
            KeyEvent.KEYCODE_EQUALS -> '='.code
            KeyEvent.KEYCODE_LEFT_BRACKET -> '['.code
            KeyEvent.KEYCODE_RIGHT_BRACKET -> ']'.code
            KeyEvent.KEYCODE_BACKSLASH -> '\\'.code
            KeyEvent.KEYCODE_SEMICOLON -> ';'.code
            KeyEvent.KEYCODE_APOSTROPHE -> '\''.code
            KeyEvent.KEYCODE_GRAVE -> '`'.code
            KeyEvent.KEYCODE_COMMA -> ','.code
            KeyEvent.KEYCODE_PERIOD -> '.'.code
            KeyEvent.KEYCODE_SLASH -> '/'.code
            KeyEvent.KEYCODE_NUMPAD_0 -> '0'.code
            KeyEvent.KEYCODE_NUMPAD_1 -> '1'.code
            KeyEvent.KEYCODE_NUMPAD_2 -> '2'.code
            KeyEvent.KEYCODE_NUMPAD_3 -> '3'.code
            KeyEvent.KEYCODE_NUMPAD_4 -> '4'.code
            KeyEvent.KEYCODE_NUMPAD_5 -> '5'.code
            KeyEvent.KEYCODE_NUMPAD_6 -> '6'.code
            KeyEvent.KEYCODE_NUMPAD_7 -> '7'.code
            KeyEvent.KEYCODE_NUMPAD_8 -> '8'.code
            KeyEvent.KEYCODE_NUMPAD_9 -> '9'.code
            KeyEvent.KEYCODE_NUMPAD_ADD -> '+'.code
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> '-'.code
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> '*'.code
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> '/'.code
            KeyEvent.KEYCODE_NUMPAD_DOT -> '.'.code
            else -> null
        }
    }

    fun controllerButtonName(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "a"
        KeyEvent.KEYCODE_BUTTON_B -> "b"
        KeyEvent.KEYCODE_BUTTON_X -> "x"
        KeyEvent.KEYCODE_BUTTON_Y -> "y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "left_shoulder"
        KeyEvent.KEYCODE_BUTTON_R1 -> "right_shoulder"
        KeyEvent.KEYCODE_BUTTON_L2 -> "left_trigger_button"
        KeyEvent.KEYCODE_BUTTON_R2 -> "right_trigger_button"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "left_stick"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "right_stick"
        KeyEvent.KEYCODE_BUTTON_START -> "start"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "select"
        KeyEvent.KEYCODE_BUTTON_MODE -> "mode"
        KeyEvent.KEYCODE_DPAD_UP -> "dpad_up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "dpad_down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "dpad_left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "dpad_right"
        KeyEvent.KEYCODE_BUTTON_C -> "c"
        KeyEvent.KEYCODE_BUTTON_Z -> "z"
        else -> null
    }
}
