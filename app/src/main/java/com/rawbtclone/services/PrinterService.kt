package com.rawbtclone.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.rawbtclone.bluetooth.PrinterManager
import com.rawbtclone.utils.EscPosBuilder
import com.rawbtclone.utils.JsonPrintParser
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class PrinterService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var serverSocket: ServerSocket? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startHttpServer()
    }

    private fun startForegroundService() {
        val channelId = "printer_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Printer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Printer Service Running")
            .setContentText("Listening on http://127.0.0.1:8080")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .build()

        startForeground(1, notification)
    }

    private fun startHttpServer() {
        serviceScope.launch {
            try {
                serverSocket = ServerSocket(8080)
                Log.d("PrinterService", "HTTP Server started on port 8080")
                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                Log.e("PrinterService", "Server error", e)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val out = PrintWriter(socket.outputStream)

            var line: String? = reader.readLine()
            var contentLength = 0
            while (line != null && line.isNotEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = line.substring(15).trim().toInt()
                }
                line = reader.readLine()
            }

            val body = CharArray(contentLength)
            reader.read(body, 0, contentLength)
            val jsonBody = String(body)

            // Minimal HTTP response
            out.println("HTTP/1.1 200 OK")
            out.println("Content-Type: application/json")
            out.println("Connection: close")
            out.println()
            out.println("{"status":"success"}")
            out.flush()
            socket.close()

            if (jsonBody.isNotEmpty()) {
                printFromJson(jsonBody)
            }
        } catch (e: Exception) {
            Log.e("PrinterService", "Error handling client", e)
        }
    }

    private fun printFromJson(json: String) {
        val printerManager = PrinterManager.getInstance(this)
        val builder = EscPosBuilder().init()
        
        try {
            JsonPrintParser.parseAndBuild(json, builder)
            
            val printData = builder.feed(3).cut().build()
            CoroutineScope(Dispatchers.Main).launch {
                printerManager.print(printData) { _, _ -> }
            }
        } catch (e: Exception) {
            Log.e("PrinterService", "Print error", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        serverSocket?.close()
    }
}
