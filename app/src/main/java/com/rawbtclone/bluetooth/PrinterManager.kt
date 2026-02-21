package com.rawbtclone.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class PrinterManager private constructor(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)

    private var connection: PrinterConnection? = null
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

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

        val device: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(address)
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
    suspend fun getPrinterBatteryLevel(callback: (Int, String?) -> Unit) {
        val address = getSavedPrinterAddress()
        if (address == null) {
            callback(-1, null)
            return
        }

        val device: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: Exception) {
            null
        }

        if (device == null) {
            callback(-1, null)
            return
        }

        // Create temporary connection for battery query
        val tempConnection = PrinterConnection(device)
        if (!tempConnection.connect()) {
            tempConnection.close()
            callback(-1, null)
            return
        }

        // Send DLE EOT (0x10 0x04) - Real-time status transmission command
        // Some printers respond with battery status
        val batteryCommand = byteArrayOf(0x10, 0x04, 0x01)
        val response = tempConnection.sendAndReceive(batteryCommand, 1000)
        
        tempConnection.close()

        if (response != null && response.isNotEmpty()) {
            // Parse battery level from response
            // For mobile printers (Eppos, etc.), bit 2 of printer status indicates low battery
            // Response format: 0xx1xx10b (fixed bits: 0,1,4,7)
            val statusByte = response.getOrElse(0) { 0 }
            
            // Check bit 2: 0 = normal, 1 = low battery (offline due to low battery)
            val isLowBattery = (statusByte.toInt() and 0x04) != 0
            
            // Check bit 3: 0 = online, 1 = offline
            val isOffline = (statusByte.toInt() and 0x08) != 0
            
            // Estimate battery level based on status bits
            val batteryLevel = when {
                isLowBattery -> 15  // Critical low battery
                isOffline -> 25     // Low battery warning
                else -> 85          // Normal (assumed)
            }
            
            val rawHex = response.joinToString(" ") { String.format("%02X", it) }.take(100)
            callback(batteryLevel, rawHex)
        } else {
            callback(-1, "No response")
        }
    }
}
