package com.rawbtclone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rawbtclone.bluetooth.PrinterManager
import com.rawbtclone.utils.EscPosBuilder
import com.rawbtclone.utils.JsonPrintParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrintIntentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.rawbtclone.PRINT") {
            val type = intent.getStringExtra("type") ?: "text"
            val dataString = intent.getStringExtra("data") ?: return

            val printerManager = PrinterManager.getInstance(context)
            val builder = EscPosBuilder().init()

            try {
                if (type == "json") {
                    JsonPrintParser.parseAndBuild(dataString, builder)
                } else {
                    builder.text(dataString).lineBreak()
                }

                val printData = builder.feed(3).cut().build()
                
                CoroutineScope(Dispatchers.Main).launch {
                    printerManager.print(printData) { success, error ->
                        if (!success) {
                            Log.e("PrintIntentReceiver", "Print failed: $error")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PrintIntentReceiver", "Error processing print intent", e)
            }
        }
    }
}
