package com.example.drumsamplebluepill

import android.content.Context
import android.hardware.usb.UsbDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper

class MidiEngine(
    context: Context,
    private val onNoteOn: (note: Int, velocity: Int) -> Unit,
    private val onNoteOff: (note: Int) -> Unit,
    private val onConnectionChanged: (connected: Boolean, deviceName: String, usbDevice: UsbDevice?) -> Unit,
) {
    private val midiManager = context.getSystemService(MidiManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var outputPort: MidiOutputPort? = null
    private var currentDevice: MidiDeviceInfo? = null

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            if (outputPort == null) connectTo(device)
        }
        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            if (device == currentDevice) disconnect()
        }
    }

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            var i = offset
            while (i < offset + count) {
                val status = msg[i].toInt() and 0xFF
                val type   = status and 0xF0
                if ((type == 0x90 || type == 0x80) && i + 2 < offset + count) {
                    val note = msg[i + 1].toInt() and 0x7F
                    val vel  = msg[i + 2].toInt() and 0x7F
                    if (type == 0x90 && vel > 0) onNoteOn(note, vel) else onNoteOff(note)
                    i += 3
                } else { i++ }
            }
        }
    }

    fun start() {
        midiManager ?: return
        midiManager.registerDeviceCallback(deviceCallback, mainHandler)
        midiManager.devices.forEach { if (outputPort == null) connectTo(it) }
    }

    private fun connectTo(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0) return
        midiManager?.openDevice(info, { device ->
            if (device == null) return@openDevice
            val port = device.openOutputPort(0)
            if (port == null) { device.close(); return@openDevice }
            port.connect(receiver)
            outputPort = port
            currentDevice = info
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI Device"
            @Suppress("DEPRECATION")
            val usbDevice = info.properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE) as? UsbDevice
            onConnectionChanged(true, name, usbDevice)
        }, mainHandler)
    }

    private fun disconnect() {
        outputPort?.close()
        outputPort = null
        currentDevice = null
        onConnectionChanged(false, "", null)
    }

    fun stop() {
        midiManager?.unregisterDeviceCallback(deviceCallback)
        disconnect()
    }
}
