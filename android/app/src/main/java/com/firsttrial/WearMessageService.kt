package com.firsttrial

import android.util.Log
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearMessageService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.e("WEAR_DATA", "🔧 WearMessageService: onCreate() called")
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.e("WEAR_DATA", "🔥🔥🔥 WearMessageService: onDataChanged called! Count: ${dataEvents.count}")
        
        for (event in dataEvents) {
            val path = event.dataItem.uri.path
            Log.e("WEAR_DATA", "Data event path: $path")
            
            // Handle both old /text-path and new /vitals-data/ paths
            if (path == "/text-path" || path?.startsWith("/vitals-data/") == true) {
                val data = event.dataItem.data
                val receivedText = data?.let { String(it) } ?: "No data"
                Log.e("WEAR_DATA", "✅ WearMessageService DataLayer: Received: $receivedText")
                sendToReactNative(receivedText)
            }
        }
        dataEvents.release()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.e("WEAR_DATA", "🔥🔥🔥 WearMessageService: onMessageReceived called! Path: ${messageEvent.path}")
        
        if (messageEvent.path == "/text-path") {
            val receivedText = String(messageEvent.data)
            Log.e("WEAR_DATA", "✅ WearMessageService Message: Received: $receivedText")
            sendToReactNative(receivedText)
        } else {
            Log.e("WEAR_DATA", "❌ Unknown path: ${messageEvent.path}")
        }
    }
    
    private fun sendToReactNative(text: String) {
        try {
            val app = application as? MainApplication
            if (app == null) {
                Log.e("WEAR_DATA", "❌ MainApplication is null")
                return
            }
            
            val reactContext = app.reactHost.currentReactContext
            if (reactContext == null) {
                Log.e("WEAR_DATA", "❌ React context is null")
                return
            }

            Log.e("WEAR_DATA", "Sending '$text' to React Native...")
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                ?.emit("WearOSMessage", text)
            Log.e("WEAR_DATA", "✅ Successfully sent to React Native!")
        } catch (e: Exception) {
            Log.e("WEAR_DATA", "❌ Failed to send to RN: ${e.message}", e)
        }
    }
}
