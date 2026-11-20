package com.example.openspeedtest

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager

enum class OledMode { Auto, On, Off }

/**
 * Android has no official "is this an OLED screen?" API, so this is a best guess:
 * - screens that support HDR video are almost always OLED, and
 * - a list of phone families known to use OLED (Pixel, Galaxy S/Note/Z/A3x+, OnePlus, Nothing…).
 * If it guesses wrong, switch OLED black On/Off in the Servers sidebar.
 */
fun isLikelyOled(context: Context): Boolean {
    val hdr = try {
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
        @Suppress("DEPRECATION")
        display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
    } catch (e: Exception) {
        false
    }

    val maker = Build.MANUFACTURER.lowercase()
    val model = Build.MODEL.uppercase()
    val knownOled = when (maker) {
        "google" -> model.startsWith("PIXEL")
        "samsung" -> Regex("^SM-(S|G9|N|F|A[3-9]|M[3-5]|E)").containsMatchIn(model)
        "oneplus", "nothing", "asus" -> true
        else -> false
    }
    return hdr || knownOled
}
