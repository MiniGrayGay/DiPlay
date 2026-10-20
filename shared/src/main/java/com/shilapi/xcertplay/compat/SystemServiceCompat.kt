package com.shilapi.xcertplay.compat

import android.app.AppOpsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.telecom.TelecomManager

/**
 * [Context.getSystemService] with a [Class] parameter requires API 23.
 * This helper resolves the service by its string constant on older platforms.
 */
@Suppress("UNCHECKED_CAST")
internal inline fun <reified T> Context.getSystemServiceCompat(): T? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return getSystemService(T::class.java)
    }
    val name = when (T::class.java) {
        UsbManager::class.java -> Context.USB_SERVICE
        AudioManager::class.java -> Context.AUDIO_SERVICE
        WifiManager::class.java -> Context.WIFI_SERVICE
        WifiP2pManager::class.java -> Context.WIFI_P2P_SERVICE
        ConnectivityManager::class.java -> Context.CONNECTIVITY_SERVICE
        LocationManager::class.java -> Context.LOCATION_SERVICE
        BluetoothManager::class.java -> Context.BLUETOOTH_SERVICE
        TelecomManager::class.java -> Context.TELECOM_SERVICE
        AppOpsManager::class.java -> Context.APP_OPS_SERVICE
        else -> return null
    }
    return getSystemService(name) as? T
}
