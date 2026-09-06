package com.obddash.app

import android.webkit.JavascriptInterface

/**
 * dashboard.html içindeki JS, "Android." önekiyle bu metodları çağırabilir.
 * Örnek: Android.connect("AA:BB:CC:DD:EE:FF")
 */
class WebAppInterface(private val btManager: ObdBluetoothManager) {

    @JavascriptInterface
    fun getPairedDevices(): String = btManager.getPairedDevicesJson()

    @JavascriptInterface
    fun connect(address: String) {
        btManager.connect(address)
    }

    @JavascriptInterface
    fun disconnect() {
        btManager.disconnect()
    }

    @JavascriptInterface
    fun clearDtc() {
        btManager.clearDtc()
    }
}
