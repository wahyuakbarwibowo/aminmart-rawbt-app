package com.rawbtclone.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*

class PrinterConnection(private val device: BluetoothDevice) {

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TAG = "PrinterConnection"
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            outputStream = socket?.outputStream
            true
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            close()
            false
        }
    }

    suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            outputStream?.write(data)
            outputStream?.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send data", e)
            false
        }
    }

    /**
     * Send data and receive response with timeout.
     * Used for status queries like battery level.
     */
    suspend fun sendAndReceive(data: ByteArray, timeoutMs: Long): ByteArray? = withContext(Dispatchers.IO) {
        try {
            outputStream?.write(data)
            outputStream?.flush()
            
            // Read response with timeout
            val buffer = ByteArrayOutputStream()
            val readBuffer = ByteArray(256)
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (socket?.inputStream?.available() ?: 0 > 0) {
                    val bytesRead = socket?.inputStream?.read(readBuffer) ?: 0
                    if (bytesRead > 0) {
                        buffer.write(readBuffer, 0, bytesRead)
                    }
                } else {
                    // Small delay to avoid busy waiting
                    kotlinx.coroutines.delay(50)
                }
            }
            
            if (buffer.size() > 0) {
                buffer.toByteArray()
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send/receive data", e)
            null
        }
    }

    fun isConnected(): Boolean {
        return socket?.isConnected == true
    }

    fun close() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        } finally {
            outputStream = null
            socket = null
        }
    }
}
