package com.firsttrial.data

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Step counter using device's step counter sensor
 * This accumulates steps throughout the day and resets at midnight
 * No OAuth or Google account required!
 */
class StepCounterManager(private val context: Context) {
    
    private val tag = "StepCounter"
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    
    private val prefs: SharedPreferences = context.getSharedPreferences("step_counter", Context.MODE_PRIVATE)
    
    private val _dailySteps = MutableStateFlow(0L)
    val dailySteps: StateFlow<Long> = _dailySteps
    
    private var stepCounterListener: SensorEventListener? = null
    private var initialStepCount: Long? = null
    
    init {
        loadSavedData()
    }
    
    /**
     * Start counting steps
     */
    fun startCounting() {
        if (stepCounterSensor == null) {
            Log.w(tag, "Step counter sensor not available on this device")
            return
        }
        
        // Check if we need to reset for a new day
        checkAndResetForNewDay()
        
        stepCounterListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    handleStepCount(event.values[0].toLong())
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(tag, "Step counter accuracy changed: $accuracy")
            }
        }
        
        sensorManager.registerListener(
            stepCounterListener,
            stepCounterSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        
        Log.d(tag, "✅ Step counter started")
    }
    
    /**
     * Stop counting steps
     */
    fun stopCounting() {
        stepCounterListener?.let {
            sensorManager.unregisterListener(it)
            Log.d(tag, "Step counter stopped")
        }
        stepCounterListener = null
    }
    
    /**
     * Handle step count from sensor
     * TYPE_STEP_COUNTER gives total steps since last reboot
     */
    private fun handleStepCount(totalSteps: Long) {
        if (initialStepCount == null) {
            initialStepCount = totalSteps
            Log.d(tag, "Initial step count set: $totalSteps")
        }
        
        val stepsSinceStart = totalSteps - (initialStepCount ?: 0L)
        val savedBaseSteps = prefs.getLong("base_steps", 0L)
        val currentDailySteps = savedBaseSteps + stepsSinceStart
        
        _dailySteps.value = currentDailySteps
        
        // Save periodically
        if (currentDailySteps % 10 == 0L) {
            saveData(currentDailySteps)
        }
    }
    
    /**
     * Check if it's a new day and reset if needed (resets at midnight)
     */
    private fun checkAndResetForNewDay() {
        val currentDate = getCurrentDate()
        val savedDate = prefs.getString("last_date", "") ?: ""
        
        if (currentDate != savedDate) {
            Log.d(tag, "New day detected! Resetting step count")
            resetForNewDay(currentDate)
        }
    }
    
    /**
     * Reset step count for a new day
     */
    private fun resetForNewDay(newDate: String) {
        prefs.edit().apply {
            putLong("daily_steps", 0L)
            putLong("base_steps", 0L)
            putString("last_date", newDate)
            apply()
        }
        
        _dailySteps.value = 0L
        initialStepCount = null
    }
    
    /**
     * Load saved data from SharedPreferences
     */
    private fun loadSavedData() {
        val savedDate = prefs.getString("last_date", getCurrentDate()) ?: getCurrentDate()
        val currentDate = getCurrentDate()
        
        if (savedDate == currentDate) {
            val savedSteps = prefs.getLong("daily_steps", 0L)
            _dailySteps.value = savedSteps
            Log.d(tag, "Loaded saved steps for today: $savedSteps")
        } else {
            resetForNewDay(currentDate)
        }
    }
    
    /**
     * Save current data to SharedPreferences
     */
    private fun saveData(steps: Long) {
        prefs.edit().apply {
            putLong("daily_steps", steps)
            putString("last_date", getCurrentDate())
            apply()
        }
    }
    
    /**
     * Get current date as string (YYYY-MM-DD)
     */
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
    
    /**
     * Get current daily steps
     */
    fun getCurrentSteps(): Long = _dailySteps.value
    
    /**
     * Calculate calories burned from steps
     * Average: 0.04 calories per step
     */
    fun getCaloriesFromSteps(steps: Long): Double {
        return steps * 0.04
    }
    
    /**
     * Calculate distance from steps (in km)
     */
    fun getDistanceFromSteps(steps: Long): Double {
        return (steps * 0.70) / 1000.0 // 0.70m average stride length
    }
}
