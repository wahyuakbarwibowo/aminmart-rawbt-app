package com.rawbtclone.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object JsonPrintParser {
    private val gson = Gson()

    fun parseAndBuild(json: String, builder: EscPosBuilder) {
        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
        val commands: List<Map<String, Any>> = try {
            gson.fromJson(json, listType)
        } catch (e: Exception) {
            try {
                val singleItem: Map<String, Any> = gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
                listOf(singleItem)
            } catch (e2: Exception) {
                // Not a valid JSON command, treat as raw text
                listOf(mapOf("type" to "text", "text" to json, "newline" to true))
            }
        }

        for (cmd in commands) {
            val align = when (cmd["align"] as? String) {
                "center" -> EscPosBuilder.ALIGN_CENTER
                "right" -> EscPosBuilder.ALIGN_RIGHT
                else -> EscPosBuilder.ALIGN_LEFT
            }
            builder.align(align)

            val bold = cmd["bold"] as? Boolean ?: false
            builder.bold(bold)

            val size = when (cmd["size"] as? String) {
                "double" -> EscPosBuilder.FONT_SIZE_DOUBLE
                "double_height" -> EscPosBuilder.FONT_SIZE_DOUBLE_HEIGHT
                "double_width" -> EscPosBuilder.FONT_SIZE_DOUBLE_WIDTH
                else -> EscPosBuilder.FONT_SIZE_NORMAL
            }
            builder.fontSize(size)

            val type = cmd["type"] as? String ?: "text"
            val text = cmd["text"] as? String ?: ""

            when (type) {
                "text" -> builder.text(text)
                "barcode" -> builder.barcode(text)
                "qr" -> builder.qrCode(text)
                "cut" -> builder.cut()
            }

            if (cmd["newline"] as? Boolean == true) {
                builder.lineBreak()
            }
        }
    }
}
