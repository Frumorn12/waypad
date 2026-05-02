package dev.waypad.android.core.externalinput

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice

private const val TAG = "WaypadExternalInput"

class ExternalInputDeviceMonitor(
    context: Context,
    private val mapper: AndroidExternalInputMapper,
    private val onDevicesChanged: (List<ExternalInputDeviceSummary>) -> Unit,
) : InputManager.InputDeviceListener {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        inputManager.registerInputDeviceListener(this, handler)
        publish("initial")
    }

    fun stop() {
        inputManager.unregisterInputDeviceListener(this)
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.i(TAG, "device_added android_id=$deviceId")
        publish("added")
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        Log.i(TAG, "device_removed android_id=$deviceId")
        mapper.clearDevice(deviceId)
        publish("removed")
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        Log.i(TAG, "device_changed android_id=$deviceId")
        publish("changed")
    }

    private fun publish(reason: String) {
        val devices = inputManager.inputDeviceIds.toList()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .map(mapper::deviceSummary)
            .filter { summary ->
                summary.isExternal && summary.classes.any { it != ExternalInputDeviceClass.Unknown }
            }
        Log.i(
            TAG,
            "device_inventory reason=$reason count=${devices.size} devices=${
                devices.joinToString { "${it.name}[${it.displayClasses}]" }
            }",
        )
        onDevicesChanged(devices)
    }
}
