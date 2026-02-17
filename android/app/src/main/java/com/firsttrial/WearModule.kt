package com.firsttrial

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WearModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    // This is the name you will use in JavaScript: NativeModules.WearModule
    override fun getName(): String {
        return "WearModule"
    }

    @ReactMethod
    fun testConnection(promise: Promise) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val test = WearConnectionTest(reactApplicationContext)
                val result = test.testConnection()
                Log.e("WEAR_DATA", "Test result:\n$result")
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("ERROR", e.message)
            }
        }
    }
    
    @ReactMethod
    fun checkListeners(promise: Promise) {
        val msg = "Listeners should be active if MainActivity is running"
        Log.e("WEAR_DATA", "📋 $msg")
        promise.resolve(msg)
    }

    // Helper function to send data to JS
    fun sendEventToJS(text: String) {
        if (reactApplicationContext.hasActiveCatalystInstance()) {
            reactApplicationContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("WearMessage", text)
        }
    }
}
