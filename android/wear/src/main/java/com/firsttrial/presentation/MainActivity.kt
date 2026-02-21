/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.firsttrial.presentation

import android.Manifest
import android.app.RemoteInput
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.tooling.preview.devices.WearDevices
import com.firsttrial.R
import com.firsttrial.data.HealthData
import com.firsttrial.data.HealthDataManager
import com.firsttrial.presentation.theme.FirstTrialTheme
import com.firsttrial.service.VitalsBackgroundService
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    private lateinit var healthDataManager: HealthDataManager
    private var hasPermissions by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check only essential permissions
        val essentialPermissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        
        val essentialGranted = essentialPermissions.all { permission ->
            permissions[permission] == true
        }
        
        hasPermissions = essentialGranted
        
        if (essentialGranted) {
            Log.d("WEAR", "✅ All essential permissions granted")
            // Start background service
            startBackgroundMonitoring()
        } else {
            val deniedEssential = essentialPermissions.filter { permissions[it] != true }
            Log.e("WEAR", "❌ Essential permissions denied: $deniedEssential")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        healthDataManager = HealthDataManager(this)

        // Check and request permissions
        checkPermissions()

        setContent {
            MyScreen()
        }
    }

    private fun checkPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            hasPermissions = true
            Log.d("WEAR", "✅ All permissions already granted")
            // Start background service
            VitalsBackgroundService.start(this)
            Log.d("WEAR", "🚀 Background service started")
        } else {
            Log.d("WEAR", "Requesting permissions: $missingPermissions")
            permissionLauncher.launch(requiredPermissions)
        }
    }
    
    private fun startBackgroundMonitoring() {
        Log.d("WEAR", "🎯 Starting background vitals monitoring service")
        VitalsBackgroundService.start(this)
    }

    fun sendMessage(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("WEAR", "Starting to send message: $text")
                val nodeClient = Wearable.getNodeClient(this@MainActivity)
                val messageClient = Wearable.getMessageClient(this@MainActivity)

                val nodes = com.google.android.gms.tasks.Tasks.await(nodeClient.connectedNodes)
                Log.d("WEAR", "✅ Connected Nodes: ${nodes.size}")

                if (nodes.isEmpty()) {
                    Log.e("WEAR", "❌ No nodes connected!")
                    return@launch
                }

                for (node in nodes) {
                    Log.d("WEAR", "Sending to node: ${node.displayName} (${node.id})")
                    val result = messageClient.sendMessage(node.id, "/text-path", text.toByteArray())
                    com.google.android.gms.tasks.Tasks.await(result)
                    Log.d("WEAR", "✅ Message sent successfully to ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e("WEAR", "❌ Error sending: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun sendVitalsData(vitalsString: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataClient = Wearable.getDataClient(this@MainActivity)
                
                // Use unique path with timestamp to ensure update triggers
                val path = "/vitals-data/${System.currentTimeMillis()}"
                val putDataReq = com.google.android.gms.wearable.PutDataRequest.create(path).apply {
                    data = vitalsString.toByteArray()
                }
                val putDataTask = dataClient.putDataItem(putDataReq.setUrgent())
                com.google.android.gms.tasks.Tasks.await(putDataTask)
                Log.d("WEAR", "✅ Vitals auto-synced: $vitalsString")
            } catch (e: Exception) {
                Log.e("WEAR", "❌ Error sending vitals: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalPagerApi::class)
    @Composable
    fun MyScreen() {
        FirstTrialTheme {
            val pagerState = rememberPagerState()
            
            // Collect health data once at the top level for all pages
            val healthData by healthDataManager.getHealthDataFlow().collectAsState(
                initial = HealthData()
            )
            
            // Start health tracking when app starts (runs continuously)
            LaunchedEffect(Unit) {
                if (hasPermissions) {
                    Log.d("WEAR", "🏃 Starting continuous health tracking...")
                } else {
                    Log.w("WEAR", "⚠️ Waiting for permissions to start tracking")
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    count = 2,
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> SendMessagePage(healthData)
                        1 -> VitalsPage(healthData)
                    }
                }

                // Page indicator at bottom
                HorizontalPagerIndicator(
                    pagerState = pagerState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    activeColor = MaterialTheme.colors.primary,
                    inactiveColor = Color.Gray
                )
            }
        }
    }

    @Composable
    fun SendMessagePage(healthData: HealthData) {
        val context = LocalContext.current
        var customMessage by remember { mutableStateOf("Hello from Watch ⌚") }
        
        // Remote input launcher for text input
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            result.data?.let { data ->
                val results = RemoteInput.getResultsFromIntent(data)
                val input = results?.getCharSequence("text_input")
                if (input != null) {
                    customMessage = input.toString()
                    Log.d("WEAR", "User typed: $customMessage")
                }
            }
        }
        
        // Auto-send vitals whenever they change
        LaunchedEffect(healthData.heartRate, healthData.steps, healthData.distance, healthData.speed) {
            val location = healthData.location
            val hasGoodGps = location?.hasAccuracy() == true && location.accuracy < 50
            
            val vitalsData = "HR:${healthData.heartRate.toInt()}|" +
                    "STEPS:${healthData.steps}|" +
                    "DIST:${String.format("%.2f", healthData.distance)}|" +
                    "CAL:${String.format("%.0f", healthData.calories)}|" +
                    "SPEED:${String.format("%.1f", healthData.speed)}|" +
                    "GPS:${if (hasGoodGps) "✓" else "⋯"}"
            
            sendVitalsData(vitalsData)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "Page 1",
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show current message (truncated if too long)
                Text(
                    text = if (customMessage.length > 30) "${customMessage.take(30)}..." else customMessage,
                    style = MaterialTheme.typography.caption1,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                // Button to type message
                Button(
                    onClick = {
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        val remoteInputs = listOf(
                            RemoteInput.Builder("text_input")
                                .setLabel("Type message")
                                .build()
                        )
                        
                        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
                        launcher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        text = "✏️ Type",
                        style = MaterialTheme.typography.button,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                
                // Button to send message
                Button(
                    onClick = {
                        sendMessage(customMessage)
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        text = "📱 Send",
                        style = MaterialTheme.typography.button,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Vitals auto-syncing...",
                    style = MaterialTheme.typography.caption2,
                    color = Color.Green
                )
            }
        }
    }

    @Composable
    fun VitalsPage(healthData: HealthData) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
        ) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(
                        text = "Health Vitals",
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricCard(
                            title = "💓 Heart",
                            value = if (healthData.heartRate > 0) "${healthData.heartRate.toInt()}" else "--",
                            unit = "BPM"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        MetricCard(
                            title = "👟 Steps",
                            value = "${healthData.steps}",
                            unit = "steps"
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricCard(
                            title = "📏 Distance",
                            value = String.format("%.2f", healthData.distance),
                            unit = "km"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        MetricCard(
                            title = "🔥 Calories",
                            value = String.format("%.0f", healthData.calories),
                            unit = "kcal"
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricCard(
                            title = "⚡ Speed",
                            value = if (healthData.speed > 0) String.format("%.1f", healthData.speed) else "0.0",
                            unit = "km/h"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        val location = healthData.location
                        val hasGoodAccuracy = location?.hasAccuracy() == true && location.accuracy < 50
                        
                        MetricCard(
                            title = "📍 GPS",
                            value = if (hasGoodAccuracy) "✓" else "⋯",
                            unit = if (hasGoodAccuracy && location != null) "${location.accuracy.toInt()}m" else ""
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    if (!hasPermissions) {
                        Text(
                            text = "⚠️ Grant permissions to start",
                            style = MaterialTheme.typography.caption2,
                            color = Color.Yellow,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "Tracking continuously...",
                            style = MaterialTheme.typography.caption3,
                            color = Color.Green,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun MetricCard(title: String, value: String, unit: String) {
        Card(
            onClick = { },
            modifier = Modifier
                .width(85.dp)
                .height(80.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.caption2,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    if (unit.isNotEmpty()) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.caption3,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WearApp(greetingName: String) {
    FirstTrialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            Greeting(greetingName = greetingName)
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}


