package com.firsttrial.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HealthDataManager(private val context: Context) {

    private val tag = "HealthDataManager"

    // Step Counter Manager - NO OAuth required!
    private val stepCounterManager = StepCounterManager(context)

    // Location for Speed
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Sensor Manager for direct sensor access
    private val sensorManager: SensorManager = 
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Heart Rate Sensors
    private val heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private val samsungHeartRateSensor: Sensor? = try {
        sensorManager.getDefaultSensor(65562) // Samsung Heart Rate Sensor
    } catch (e: Exception) {
        null
    }

    init {
        // Log available sensors
        Log.i(tag, "═══ HealthDataManager Initialized ═══")
        Log.i(tag, "Heart Rate Sensor: ${if (heartRateSensor != null) "✓ Available" else "✗ Not available"}")
        Log.i(tag, "Samsung HR Sensor: ${if (samsungHeartRateSensor != null) "✓ Available" else "✗ Not available"}")
        
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        Log.i(tag, "Total sensors available: ${allSensors.size}")
        allSensors.forEach { sensor ->
            if (sensor.type == Sensor.TYPE_HEART_RATE || sensor.type == 65562) {
                Log.i(tag, "  HR Sensor found: ${sensor.name} (Type: ${sensor.type})")
            }
        }
    }

    private val bgScope = CoroutineScope(Dispatchers.Default)
    private var sensorEventListener: SensorEventListener? = null
    private var locationCallback: LocationCallback? = null

    /**
     * Get health data flow with real-time updates
     */
    fun getHealthDataFlow(): Flow<HealthData> = callbackFlow {
        val producerScope = this
        Log.i(tag, "Starting health data collection")

        // Start Step Counter
        stepCounterManager.startCounting()
        Log.i(tag, "✅ Step counter started")

        var currentData = HealthData()
        val mutex = Mutex()

        // Helper to update and emit data safely
        suspend fun updateData(block: (HealthData) -> HealthData) {
            mutex.withLock {
                val newData = block(currentData)
                if (newData != currentData) {
                    currentData = newData
                    trySend(currentData)
                }
            }
        }

        // Heart Rate Sensor Listener
        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_HEART_RATE, 65562 -> {
                        val hr = event.values[0].toDouble()
                        Log.d(tag, "❤️ Heart Rate received: ${hr.toInt()} BPM (from sensor type: ${event.sensor.type})")
                        if (hr > 0) {
                            Log.i(tag, "✅ Valid Heart Rate: ${hr.toInt()} BPM")
                            producerScope.launch {
                                updateData { it.copy(heartRate = hr) }
                            }
                        } else {
                            Log.w(tag, "⚠️ Invalid heart rate value: $hr")
                        }
                    }
                    else -> {
                        Log.w(tag, "Unknown sensor event: ${event.sensor.type}")
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                val accuracyStr = when (accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
                    SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
                    else -> "NO_CONTACT"
                }
                Log.d(tag, "Sensor accuracy changed: ${sensor?.name} = $accuracyStr")
                
                if (accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) {
                    Log.w(tag, "⚠️ NO CONTACT - Make sure watch is worn snugly on wrist")
                }
            }
        }

        // Register Heart Rate Sensor
        try {
            var sensorsRegistered = 0
            
            // Try standard heart rate sensor first
            if (heartRateSensor != null) {
                val registered = sensorManager.registerListener(
                    sensorEventListener, 
                    heartRateSensor, 
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                if (registered) {
                    Log.i(tag, "✓ Standard Heart Rate sensor registered successfully")
                    sensorsRegistered++
                } else {
                    Log.w(tag, "✗ Failed to register standard Heart Rate sensor")
                }
            }
            
            // Also try Samsung sensor (some watches need both)
            if (samsungHeartRateSensor != null) {
                val registered = sensorManager.registerListener(
                    sensorEventListener, 
                    samsungHeartRateSensor, 
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                if (registered) {
                    Log.i(tag, "✓ Samsung Heart Rate sensor registered successfully")
                    sensorsRegistered++
                } else {
                    Log.w(tag, "✗ Failed to register Samsung Heart Rate sensor")
                }
            }
            
            if (sensorsRegistered == 0) {
                Log.e(tag, "❌ NO HEART RATE SENSORS REGISTERED!")
                Log.e(tag, "   This might be a permission issue. Check BODY_SENSORS permission.")
            } else {
                Log.i(tag, "✅ Total HR sensors registered: $sensorsRegistered")
            }
        } catch (e: SecurityException) {
            Log.e(tag, "❌ SECURITY EXCEPTION: ${e.message}")
            Log.e(tag, "   BODY_SENSORS permission not granted!")
        } catch (e: Exception) {
            Log.e(tag, "❌ Error registering sensors: ${e.message}", e)
        }

        // Periodic Step Counter check
        bgScope.launch {
            while (true) {
                try {
                    val steps = stepCounterManager.getCurrentSteps()
                    val distance = stepCounterManager.getDistanceFromSteps(steps)
                    val calories = stepCounterManager.getCaloriesFromSteps(steps)
                    
                    Log.d(tag, "📊 Steps: $steps | Distance: ${String.format("%.2f", distance)}km | Calories: ${String.format("%.0f", calories)}kcal")
                    
                    producerScope.launch {
                        updateData { current ->
                            current.copy(
                                steps = steps,
                                distance = distance,
                                calories = calories
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "❌ Step counter check error: ${e.message}")
                }
                
                try {
                    kotlinx.coroutines.delay(3000) // Check every 3 seconds
                } catch (e: Exception) {
                    break
                }
            }
        }

        // Location for Speed
        bgScope.launch {
            try {
                if (hasLocationPermission()) {
                    Log.i(tag, "✓ Location permission granted, starting location updates")
                    val callback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            locationResult.lastLocation?.let { location ->
                                Log.d(tag, "📍 Location: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}m")
                                
                                producerScope.launch {
                                    updateData { current ->
                                        var newState = current.copy(location = location)
                                        
                                        if (location.hasSpeed()) {
                                            val speedKmh = location.speed * 3.6 // m/s to km/h
                                            Log.d(tag, "⚡ Speed: ${String.format("%.1f", speedKmh)} km/h")
                                            newState = newState.copy(speed = speedKmh)
                                        }
                                        newState
                                    }
                                }
                            }
                        }
                    }
                    
                    locationCallback = callback
                    fusedLocationClient.requestLocationUpdates(
                        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).build(),
                        callback,
                        Looper.getMainLooper()
                    )
                    Log.i(tag, "✅ Location updates active")
                } else {
                    Log.w(tag, "❌ Location permission NOT granted")
                }
            } catch (e: SecurityException) {
                Log.e(tag, "❌ SECURITY EXCEPTION for location: ${e.message}")
            } catch (e: Exception) {
                Log.e(tag, "❌ Error requesting location updates: ${e.message}", e)
            }
        }

        awaitClose {
            Log.i(tag, "Stopping health data collection")
            try {
                if (sensorEventListener != null) {
                    sensorManager.unregisterListener(sensorEventListener)
                }
                
                stepCounterManager.stopCounting()
                
                if (hasLocationPermission() && locationCallback != null) {
                    fusedLocationClient.removeLocationUpdates(locationCallback!!)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error during cleanup", e)
            }
        }
    }.catch { e ->
        Log.e(tag, "Flow error: ${e.message}", e)
        emit(HealthData())
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun stopCollection() {
        Log.i(tag, "Manually stopping all data collection")
        try {
            if (sensorEventListener != null) {
                sensorManager.unregisterListener(sensorEventListener)
                sensorEventListener = null
            }
            
            stepCounterManager.stopCounting()
            
            if (hasLocationPermission() && locationCallback != null) {
                fusedLocationClient.removeLocationUpdates(locationCallback!!)
                locationCallback = null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during manual stop: ${e.message}", e)
        }
    }
}
