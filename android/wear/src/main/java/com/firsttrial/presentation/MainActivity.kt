/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.firsttrial.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.firsttrial.R
import com.firsttrial.presentation.theme.FirstTrialTheme
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            MyScreen()
        }
    }

    fun sendMessage(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("WEAR", "Starting to send message: $text")
                val dataClient = Wearable.getDataClient(this@MainActivity)
                val capabilityClient = Wearable.getCapabilityClient(this@MainActivity)
                
                // Send using DataLayer (more reliable than MessageClient)
                val putDataReq = com.google.android.gms.wearable.PutDataRequest.create("/text-path").apply {
                    data = text.toByteArray()
                }
                val putDataTask = dataClient.putDataItem(putDataReq.setUrgent())
                com.google.android.gms.tasks.Tasks.await(putDataTask)
                Log.d("WEAR", "✅ Data sent via DataLayer")
                
                // Also send via MessageClient as backup
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

    @Composable
    fun MyScreen() {
        FirstTrialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        sendMessage("Hello from Watch ⌚")
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text(
                        text = "📱 Send to Phone",
                        style = MaterialTheme.typography.button,
                        textAlign = TextAlign.Center
                    )
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


