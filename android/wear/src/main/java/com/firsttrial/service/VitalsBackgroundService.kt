package com.firsttrial.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.firsttrial.R
import com.firsttrial.data.HealthData
import com.firsttrial.data.HealthDataManager
import com.firsttrial.presentation.MainActivity
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class VitalsBackgroundService : Service() {

    private val tag = "VitalsService"
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var healthDataManager: HealthDataManager
    private var dataCollectionJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vitals_background_channel"
        private const val CHANNEL_NAME = "Vitals Monitoring"
        
        fun start(context: Context) {
            val intent = Intent(context, VitalsBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VitalsBackgroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "🚀 VitalsBackgroundService created")
        
        healthDataManager = HealthDataManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        startDataCollection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(tag, "📡 VitalsBackgroundService started")
        return START_STICKY // Restart service if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDataCollection() {
        dataCollectionJob = serviceScope.launch {
            healthDataManager.getHealthDataFlow()
                .catch { e ->
                    Log.e(tag, "❌ Error in health data flow: ${e.message}", e)
                }
                .collect { healthData ->
                    Log.d(tag, "📊 Data: HR=${healthData.heartRate.toInt()}, Steps=${healthData.steps}")
                    sendVitalsToPhone(healthData)
                    updateNotification(healthData)
                }
        }
    }

    private fun sendVitalsToPhone(healthData: HealthData) {
        serviceScope.launch {
            try {
                val steps = healthData.steps
                val distance = healthData.distance
                val calories = healthData.calories
                val speed = healthData.speed
                val gpsQuality = if (healthData.location != null && healthData.location.accuracy < 50) "✓" else "✗"

                val vitalsData = "HR:${healthData.heartRate.toInt()}|STEPS:$steps|DIST:%.2f|CAL:$calories|SPEED:%.1f|GPS:$gpsQuality"
                    .format(distance, speed)

                val dataClient = Wearable.getDataClient(applicationContext)
                val putDataReq = PutDataMapRequest.create("/vitals-data/${System.currentTimeMillis()}").apply {
                    dataMap.putString("vitals", vitalsData)
                }.asPutDataRequest()

                dataClient.putDataItem(putDataReq).addOnSuccessListener {
                    Log.d(tag, "✅ Vitals sent: $vitalsData")
                }.addOnFailureListener { e ->
                    Log.e(tag, "❌ Failed to send vitals: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ Error sending vitals: ${e.message}", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous vitals monitoring"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(healthData: HealthData? = null): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (healthData != null) {
            "HR: ${healthData.heartRate.toInt()} | Steps: ${healthData.steps}"
        } else {
            "Monitoring vitals..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vitals Monitoring Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Use system icon
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(healthData: HealthData) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(healthData))
    }

    override fun onDestroy() {
        Log.i(tag, "🛑 VitalsBackgroundService destroyed")
        dataCollectionJob?.cancel()
        serviceScope.cancel()
        healthDataManager.stopCollection()
        super.onDestroy()
    }
}
