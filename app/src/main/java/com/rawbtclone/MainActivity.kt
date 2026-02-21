package com.rawbtclone

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.rawbtclone.bluetooth.BluetoothDiscoveryManager
import com.rawbtclone.bluetooth.PrinterManager
import com.rawbtclone.services.PrinterService
import com.rawbtclone.utils.EscPosBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothDiscoveryManager: BluetoothDiscoveryManager
    private lateinit var printerManager: PrinterManager
    
    private lateinit var tvStatus: TextView
    private lateinit var lvDevices: ListView
    private lateinit var btnScan: Button
    private lateinit var btnTestPrint: Button
    private lateinit var btnStopService: Button

    private var devicesList: List<BluetoothDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothDiscoveryManager = BluetoothDiscoveryManager(this)
        printerManager = PrinterManager.getInstance(this)

        tvStatus = findViewById(R.id.tvStatus)
        lvDevices = findViewById(R.id.lvDevices)
        btnScan = findViewById(R.id.btnScan)
        btnTestPrint = findViewById(R.id.btnTestPrint)
        btnStopService = findViewById(R.id.btnStopService)

        btnScan.setOnClickListener {
            if (checkPermissions()) {
                refreshDevices()
            } else {
                requestPermissions()
            }
        }

        btnTestPrint.setOnClickListener {
            testPrint()
        }

        btnStopService.setOnClickListener {
            stopPrinterService()
        }

        lvDevices.setOnItemClickListener { _, _, position, _ ->
            val device = devicesList[position]
            printerManager.savePrinterAddress(device.address)
            updateStatus()
            Toast.makeText(this, "Selected: ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
        }

        updateStatus()
        if (checkPermissions()) {
            refreshDevices()
        }

        // Start local HTTP server service
        val serviceIntent = Intent(this, PrinterService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun updateStatus() {
        val address = printerManager.getSavedPrinterAddress()
        if (address != null) {
            tvStatus.text = "Saved Printer: $address"
        } else {
            tvStatus.text = getString(R.string.status_disconnected)
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshDevices() {
        devicesList = bluetoothDiscoveryManager.getPairedDevices()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            devicesList.map { "${it.name ?: "Unknown"}\n${it.address}" }
        )
        lvDevices.adapter = adapter
    }

    private fun testPrint() {
        val data = EscPosBuilder()
            .init()
            .align(EscPosBuilder.ALIGN_CENTER)
            .fontSize(EscPosBuilder.FONT_SIZE_NORMAL)
            .bold(true)
            .text("AMINMART RAWBT TEST")
            .lineBreak()
            .bold(false)
            .fontSize(EscPosBuilder.FONT_SIZE_NORMAL)
            .text("Testing Printer Connection")
            .lineBreak()
            .align(EscPosBuilder.ALIGN_LEFT)
            .text("--------------------------------")
            .lineBreak()
            .text("Item 1              Rp 10.000")
            .lineBreak()
            .text("Item 2              Rp 20.000")
            .lineBreak()
            .text("--------------------------------")
            .lineBreak()
            .align(EscPosBuilder.ALIGN_RIGHT)
            .bold(true)
            .text("TOTAL: Rp 30.000")
            .lineBreak()
            .lineBreak()
            .align(EscPosBuilder.ALIGN_CENTER)
            .qrCode("https://github.com/wahyu")
            .feed(2)
            .cut()
            .build()

        lifecycleScope.launch {
            printerManager.print(data) { success, error ->
                if (success) {
                    Toast.makeText(this@MainActivity, "Print success", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Print failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopPrinterService() {
        // Clear saved printer address
        printerManager.clearPrinterAddress()
        
        // Stop the service
        val serviceIntent = Intent(this, PrinterService::class.java)
        stopService(serviceIntent)
        
        // Update status
        updateStatus()
        
        Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        ActivityCompat.requestPermissions(this, permissions, 100)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            refreshDevices()
        } else {
            Toast.makeText(this, "Permissions required for Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }
}
