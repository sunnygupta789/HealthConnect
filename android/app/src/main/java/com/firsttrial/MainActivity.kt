package com.firsttrial

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.facebook.react.modules.core.DeviceEventManagerModule

class MainActivity : ReactActivity() {

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "firstTrial"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
      
  private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
    Log.e("WEAR_DATA", "🔥🔥🔥 MainActivity listener TRIGGERED! Path: ${messageEvent.path}")
    
    // Show toast for debugging
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(this, "Message received: ${messageEvent.path}", Toast.LENGTH_LONG).show()
    }
    
    if (messageEvent.path == "/text-path") {
      val receivedText = String(messageEvent.data)
      Log.e("WEAR_DATA", "🔥 MainActivity: Message data: $receivedText")
      sendToReactNative(receivedText)
    } else {
      Log.e("WEAR_DATA", "❌ Unknown path: ${messageEvent.path}")
    }
  }
  
  private val dataListener = com.google.android.gms.wearable.DataClient.OnDataChangedListener { dataEvents ->
    Log.e("WEAR_DATA", "🔥🔥🔥 MainActivity DataListener TRIGGERED! Count: ${dataEvents.count}")
    
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(this, "Data received!", Toast.LENGTH_LONG).show()
    }
    
    for (event in dataEvents) {
      val path = event.dataItem.uri.path
      Log.e("WEAR_DATA", "Data path: $path")
      
      if (path == "/text-path") {
        val data = event.dataItem.data
        val receivedText = data?.let { String(it) } ?: "No data"
        Log.e("WEAR_DATA", "🔥 MainActivity: Data: $receivedText")
        sendToReactNative(receivedText)
      }
    }
    dataEvents.release()
  }
  
  override fun onCreate(savedInstanceState: Bundle?) {
    Log.e("WEAR_DATA", "🚀 MainActivity onCreate - Registering listeners...")
    super.onCreate(savedInstanceState)
    
    try {
      // Register both message and data listeners
      Wearable.getMessageClient(this).addListener(messageListener)
      Wearable.getDataClient(this).addListener(dataListener)
      Log.e("WEAR_DATA", "✅ MainActivity: Both listeners REGISTERED")
      
      Toast.makeText(this, "WearOS listeners registered", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
      Log.e("WEAR_DATA", "❌ Failed to register listeners: ${e.message}", e)
    }
  }
  
  private fun sendToReactNative(text: String) {
    try {
      val app = application as? MainApplication
      val reactContext = app?.reactHost?.currentReactContext
      
      if (reactContext == null) {
        Log.e("WEAR_DATA", "❌ React context is null")
        return
      }
      
      reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        ?.emit("WearOSMessage", text)
      Log.e("WEAR_DATA", "✅ MainActivity: Sent to React Native")
    } catch (e: Exception) {
      Log.e("WEAR_DATA", "❌ Failed to send to RN: ${e.message}", e)
    }
  }
  
  override fun onResume() {
    super.onResume()
    Log.e("WEAR_DATA", "📱 MainActivity onResume - Listener should be active")
  }
  
  override fun onDestroy() {
    super.onDestroy()
    Log.e("WEAR_DATA", "💀 MainActivity onDestroy - Removing listeners")
    try {
      Wearable.getMessageClient(this).removeListener(messageListener)
      Wearable.getDataClient(this).removeListener(dataListener)
    } catch (e: Exception) {
      Log.e("WEAR_DATA", "Error removing listeners: ${e.message}")
    }
  }
}
