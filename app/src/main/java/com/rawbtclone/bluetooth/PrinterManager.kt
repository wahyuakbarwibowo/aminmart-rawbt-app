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
}
