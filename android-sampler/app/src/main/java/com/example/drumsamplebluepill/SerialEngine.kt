package com.example.drumsamplebluepill

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SerialEngine(
    context: Context,
    private val onLine: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
) {
    private val usbManager = context.getSystemService(UsbManager::class.java)
    private var connection: UsbDeviceConnection? = null
    private var bulkIn: UsbEndpoint? = null
    private var bulkOut: UsbEndpoint? = null
    private var dataIface: UsbInterface? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null

    fun connect(device: UsbDevice) {
        if (connection != null) return
        if (usbManager == null || !usbManager.hasPermission(device)) return

        var ctrlIface: UsbInterface? = null
        var dataInterface: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            when (intf.interfaceClass) {
                UsbConstants.USB_CLASS_COMM     -> ctrlIface = intf
                UsbConstants.USB_CLASS_CDC_DATA -> dataInterface = intf
            }
        }
        dataInterface ?: return

        val conn = usbManager.openDevice(device) ?: return
        ctrlIface?.let { conn.claimInterface(it, true) }
        if (!conn.claimInterface(dataInterface, true)) { conn.close(); return }

        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (i in 0 until dataInterface.endpointCount) {
            val ep = dataInterface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
            }
        }
        if (inEp == null || outEp == null) { conn.close(); return }

        // SET_LINE_CODING 115200 8N1
        val lc = ByteArray(7).apply {
            this[0] = 0x00; this[1] = 0xC2.toByte(); this[2] = 0x01; this[3] = 0x00
            this[4] = 0x00; this[5] = 0x00; this[6] = 0x08
        }
        conn.controlTransfer(0x21, 0x20, 0, ctrlIface?.id ?: 0, lc, lc.size, 1000)
        conn.controlTransfer(0x21, 0x22, 0x03, ctrlIface?.id ?: 0, null, 0, 1000)

        connection = conn
        bulkIn = inEp
        bulkOut = outEp
        dataIface = dataInterface
        onConnectionChanged(true)
        startReading()
    }

    private fun startReading() {
        readJob = scope.launch {
            val buf = ByteArray(64)
            val line = StringBuilder()
            while (isActive) {
                val conn = connection ?: break
                val ep = bulkIn ?: break
                val len = conn.bulkTransfer(ep, buf, buf.size, 200)
                if (len > 0) {
                    String(buf, 0, len, Charsets.UTF_8).forEach { c ->
                        if (c == '\n' || c == '\r') {
                            val s = line.toString().trim()
                            if (s.isNotEmpty()) withContext(Dispatchers.Main) { onLine(s) }
                            line.clear()
                        } else {
                            line.append(c)
                        }
                    }
                }
            }
        }
    }

    fun sendLine(text: String) {
        scope.launch {
            val bytes = (text + "\n").toByteArray(Charsets.UTF_8)
            connection?.bulkTransfer(bulkOut, bytes, bytes.size, 1000)
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        dataIface?.let { connection?.releaseInterface(it) }
        connection?.close()
        connection = null
        bulkIn = null
        bulkOut = null
        onConnectionChanged(false)
    }

    fun release() {
        disconnect()
    }
}
