package rs.fon.hakaton.audionav

import android.util.Log

object LogTag {
    const val APP = "APP"
    const val BLE_ADV = "BLE_ADV"
    const val BLE_SCAN = "BLE_SCAN"
    const val RSSI = "RSSI"
    const val TTS = "TTS"
    const val STORE = "STORE"
}

object AppLogger {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
