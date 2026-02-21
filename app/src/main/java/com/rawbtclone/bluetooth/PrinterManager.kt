package com.rawbtclone.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class PrinterManager private constructor(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)
    
    private var connection: PrinterConnection? = null

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: PrinterManager? = null

        fun getInstance(context: Context): PrinterManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrinterManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun savePrinterAddress(address: String) {
        sharedPreferences.edit().putString("last_printer_address", address).apply()
    }

    fun getSavedPrinterAddress(): String? {
        return sharedPreferences.getString("last_printer_address", null)
    }

    fun clearPrinterAddress() {
        sharedPreferences.edit().remove("last_printer_address").apply()
    }

    @SuppressLint("MissingPermission")
    suspend fun print(data: ByteArray, callback: (Boolean, String?) -> Unit) {
        val address = getSavedPrinterAddress()
        if (address == null) {
            callback(false, "No printer selected")
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            null
        }

        if (device == null) {
            callback(false, "Printer device not found")
            return
        }

        if (connection == null || !connection!!.isConnected()) {
            connection?.close()
            connection = PrinterConnection(device)
            if (!connection!!.connect()) {
                callback(false, "Failed to connect to printer")
                return
            }
        }

        val success = connection!!.sendData(data)
        if (success) {
            callback(true, null)
        } else {
            connection?.close()
            callback(false, "Failed to send data")
        }
    }
    
    fun closeConnection() {
        connection?.close()
        connection = null
    }

    /**
     * Query battery level from printer using ESC/POS real-time status command.
     * Returns battery percentage (0-100) or -1 if failed/unsupported.
     * 
     * Uses DLE EOT (0x10 0x04) command to request printer status.
     * Note: Works on printers that support real-time status transmission.
     */
    suspend fun getPrinterBatteryLevel(callback: (Int) -> Unit) {
        val address = getSavedPrinterAddress()
        if (address == null) {
            callback(-1)
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice? = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            null
        }

        if (device == null) {
            callback(-1)
            return
        }

        // Create temporary connection for battery query
        val tempConnection = PrinterConnection(device)
        if (!tempConnection.connect()) {
            tempConnection.close()
            callback(-1)
            return
        }

        // Send DLE EOT (0x10 0x04) - Real-time status transmission command
        // Some printers respond with battery status
        val batteryCommand = byteArrayOf(0x10, 0x04, 0x01)
        val response = tempConnection.sendAndReceive(batteryCommand, 1000)
        
        tempConnection.close()

        if (response != null && response.isNotEmpty()) {
            // Parse battery level from response
            // Response format varies by manufacturer
            // Common: second byte contains battery info
            val batteryByte = response.getOrElse(1) { 0 }
            val batteryLevel = when {
                batteryByte.toInt() == 0x00 -> 100  // Full
                batteryByte.toInt() == 0x01 -> 75   // Medium
                batteryByte.toInt() == 0x02 -> 50   // Low
                batteryByte.toInt() == 0x03 -> 25   // Critical
                batteryByte.toInt() in 0..100 -> batteryByte.toInt() // Direct percentage
                else -> -1  // Unknown/unsupported
            }
            callback(batteryLevel)
        } else {
            callback(-1)  // No response or unsupported command
        }
    }
}
