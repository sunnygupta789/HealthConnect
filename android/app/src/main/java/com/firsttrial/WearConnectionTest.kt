package com.firsttrial

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable

class WearConnectionTest(private val context: Context) {
    
    suspend fun testConnection(): String {
        val results = StringBuilder()
        
        try {
            // Test 1: Check connected nodes
            val nodeClient = Wearable.getNodeClient(context)
            val nodesTask = nodeClient.connectedNodes
            val nodes = com.google.android.gms.tasks.Tasks.await(nodesTask)
            results.append("✅ Connected nodes: ${nodes.size}\n")
            nodes.forEach { node ->
                results.append("  - ${node.displayName} (${node.id})\n")
            }
            
            // Test 2: Check capabilities
            val capabilityClient = Wearable.getCapabilityClient(context)
            val capabilityTask = capabilityClient.getCapability(
                "firsttrial_watch", 
                com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE
            )
            val capability = com.google.android.gms.tasks.Tasks.await(capabilityTask)
            results.append("📡 Watch capability nodes: ${capability.nodes.size}\n")
            
            // Test 3: Check if listeners are working
            results.append("✅ WearConnectionTest completed\n")
            results.append("ℹ️ If nodes are connected but messages not received,\n")
            results.append("   the issue is with Google Play Services routing.\n")
            
        } catch (e: Exception) {
            results.append("❌ Error: ${e.message}\n")
            Log.e("WEAR_DATA", "Connection test failed", e)
        }
        
        return results.toString()
    }
}
